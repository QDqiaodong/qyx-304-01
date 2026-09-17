package com.volunteer.service;

import com.alibaba.fastjson.JSON;
import com.volunteer.dto.request.RegistrationRequest;
import com.volunteer.dto.response.ApprovalFlowDetail;
import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.dto.response.RegistrationDetail;
import com.volunteer.entity.*;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RegistrationService {

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private VolunteerRepository volunteerRepository;

    @Autowired
    private ApprovalFlowRepository approvalFlowRepository;

    @Autowired
    private CapabilityValidationService capabilityValidationService;

    @Autowired
    private ApprovalFlowService approvalFlowService;

    @Autowired
    private PositionCacheService positionCacheService;

    @Transactional
    public Registration createRegistration(RegistrationRequest request) {
        // 锁顺序与散场扫描、通过动作一致：先活动后岗位，杜绝「散场瞬间新报名仍送进来」
        Activity activity = activityRepository.findByIdForUpdate(request.getActivityId()).orElse(null);
        if (activity == null) {
            throw new IllegalArgumentException("活动不存在");
        }
        if (capabilityValidationService.isActivityEnded(activity)) {
            throw new IllegalStateException("活动已结束，新报名失败");
        }

        Position position = positionRepository.findByIdForUpdate(request.getPositionId()).orElse(null);

        // 持锁现场校验：最新门槛 + 证书有效期 + 活动时效，三者任一不过即驳回、不占名额
        CapabilityCheckResult checkResult = capabilityValidationService.validateAgainstContext(
                request.getVolunteerId(), position, activity);

        Registration registration = new Registration();
        registration.setVolunteerId(request.getVolunteerId());
        registration.setActivityId(request.getActivityId());
        registration.setPositionId(request.getPositionId());
        registration.setApplyMessage(request.getApplyMessage());
        registration.setCapabilityCheckResult(JSON.toJSONString(checkResult));
        registration.setCheckPass(checkResult.getPass() ? 1 : 0);
        registration.setRequirementVersionAtApply(position == null || position.getRequirementVersion() == null
                ? 1 : position.getRequirementVersion());

        if (checkResult.getPass()) {
            registration.setStatus(ApprovalStatus.PENDING.getCode());
            registration.setCurrentApprovalNode(ApprovalNode.LEADER.getLevel());
        } else {
            registration.setStatus(ApprovalStatus.REJECTED.getCode());
            registration.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
        }

        registration = registrationRepository.save(registration);

        if (checkResult.getPass()) {
            approvalFlowService.createApprovalFlow(registration.getId());
        }

        if (position != null) {
            positionCacheService.cachePositionCapability(request.getPositionId());
        }

        return registration;
    }

    /**
     * 退回修改后重新送审：
     * 仅退回状态的单子可重提；能力校验按当前最新门槛重跑，
     * 通过则清空旧审批记录、从组长节点两个审批节点全部从头来，绝不接回退回前节点；
     * 不通过则停在能力校验失败，不占名额。重提成功前该单一直不占岗位人数。
     */
    @Transactional
    public RegistrationDetail resubmit(Long registrationId, String applyMessage) {
        Registration unlocked = registrationRepository.findById(registrationId).orElse(null);
        if (unlocked == null) {
            throw new IllegalArgumentException("报名单不存在");
        }

        // 锁顺序与改门槛、通过、散场扫描一致：先活动，再岗位，最后报名，防止互锁
        Activity activity = activityRepository.findByIdForUpdate(unlocked.getActivityId())
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
        if (capabilityValidationService.isActivityEnded(activity)) {
            throw new IllegalStateException("活动已结束，无法重新送审");
        }

        Position position = positionRepository.findByIdForUpdate(unlocked.getPositionId())
                .orElseThrow(() -> new IllegalArgumentException("岗位不存在"));

        Registration registration = registrationRepository.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("报名单不存在"));
        if (!ApprovalStatus.RETURNED.getCode().equals(registration.getStatus())) {
            throw new IllegalStateException("只有退回修改的报名单可以重新送审");
        }

        // 持锁现场校验：当前门槛 + 证书有效期 + 活动时效
        CapabilityCheckResult checkResult = capabilityValidationService.validateAgainstContext(
                registration.getVolunteerId(), position, activity);

        if (applyMessage != null) {
            registration.setApplyMessage(applyMessage);
        }
        registration.setCapabilityCheckResult(JSON.toJSONString(checkResult));
        registration.setCheckPass(checkResult.getPass() ? 1 : 0);
        registration.setRequirementVersionAtApply(position.getRequirementVersion());
        // 重提按新门槛重新校验，旧复核结论作废
        registration.setRecheckResult(null);
        registration.setRecheckPass(null);
        registration.setResumeNode(null);

        // 旧审批记录一律删除：两个审批节点从头重跑，不许接着退回前的节点批
        approvalFlowRepository.deleteByRegistrationId(registrationId);

        if (checkResult.getPass()) {
            registration.setStatus(ApprovalStatus.PENDING.getCode());
            registration.setCurrentApprovalNode(ApprovalNode.LEADER.getLevel());
            registrationRepository.save(registration);
            approvalFlowService.createApprovalFlow(registrationId);
        } else {
            // 重提即按当前门槛重新报名，不通过与新报名失败一致：驳回、不占名额
            registration.setStatus(ApprovalStatus.REJECTED.getCode());
            registration.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
            registrationRepository.save(registration);
        }

        return getRegistrationDetail(registrationId);
    }

    public RegistrationDetail getRegistrationDetail(Long registrationId) {
        Registration registration = registrationRepository.findById(registrationId).orElse(null);
        if (registration == null) {
            return null;
        }

        RegistrationDetail detail = new RegistrationDetail();
        detail.setId(registration.getId());

        Volunteer volunteer = volunteerRepository.findById(registration.getVolunteerId()).orElse(null);
        if (volunteer != null) {
            detail.setVolunteerName(volunteer.getName());
            detail.setVolunteerPhone(volunteer.getPhone());
        }

        Activity activity = activityRepository.findById(registration.getActivityId()).orElse(null);
        if (activity != null) {
            detail.setActivityName(activity.getName());
        }

        Position position = positionRepository.findById(registration.getPositionId()).orElse(null);
        if (position != null) {
            detail.setPositionName(position.getName());
            detail.setCurrentRequirementVersion(position.getRequirementVersion());
        }

        // 现场时效（非报名时快照）：证件是否过期、活动是否散场，决定列表过期色与通过按钮是否还活着
        boolean activityEnded = activity != null && capabilityValidationService.isActivityEnded(activity);
        boolean certExpired = position != null
                && capabilityValidationService.hasExpiredRequiredCertificate(
                        registration.getVolunteerId(), position);
        detail.setActivityEnded(activityEnded);
        detail.setCertExpired(certExpired);

        boolean occupyingLike = ApprovalStatus.PENDING.getCode().equals(registration.getStatus())
                || ApprovalStatus.APPROVED.getCode().equals(registration.getStatus())
                || ApprovalStatus.COMPLETED.getCode().equals(registration.getStatus())
                || ApprovalStatus.CHECK_FAILED.getCode().equals(registration.getStatus());
        boolean timeBlocked = occupyingLike && (activityEnded || certExpired);
        detail.setTimeBlocked(timeBlocked);
        if (timeBlocked) {
            List<String> reasons = new ArrayList<>();
            if (certExpired) {
                reasons.add("必备证书已过有效期");
            }
            if (activityEnded) {
                reasons.add("活动已散场");
            }
            detail.setBlockReason(String.join("、", reasons));
        }

        detail.setApplyMessage(registration.getApplyMessage());
        detail.setStatus(registration.getStatus());
        detail.setStatusDesc(ApprovalStatus.fromCode(registration.getStatus()).getDesc());
        detail.setCheckPass(registration.getCheckPass());
        detail.setCheckPassDesc(registration.getCheckPass() == 1 ? "通过" : "未通过");
        detail.setCapabilityCheckResult(registration.getCapabilityCheckResult());
        detail.setRequirementVersionAtApply(registration.getRequirementVersionAtApply());
        detail.setRecheckResult(registration.getRecheckResult());
        detail.setRecheckPass(registration.getRecheckPass());
        if (registration.getRecheckPass() != null) {
            detail.setRecheckPassDesc(registration.getRecheckPass() == 1 ? "复核通过" : "复核未通过");
        }
        // 有效校验：门槛改写后以最新复核为准，否则以报名时校验为准
        int effectivePass = registration.getRecheckPass() != null
                ? registration.getRecheckPass()
                : (registration.getCheckPass() == null ? 0 : registration.getCheckPass());
        detail.setEffectivePass(effectivePass);
        detail.setEffectivePassDesc(effectivePass == 1 ? "通过" : "未通过");
        detail.setCurrentApprovalNode(registration.getCurrentApprovalNode());
        detail.setCurrentApprovalNodeDesc(ApprovalNode.fromLevel(registration.getCurrentApprovalNode()).getName());
        detail.setCreatedAt(registration.getCreatedAt());
        detail.setUpdatedAt(registration.getUpdatedAt());

        List<ApprovalFlow> flows = approvalFlowRepository.findByRegistrationIdOrderByNodeLevelAsc(registrationId);
        detail.setApprovalFlows(flows.stream().map(this::convertToApprovalFlowDetail).collect(Collectors.toList()));

        return detail;
    }

    private ApprovalFlowDetail convertToApprovalFlowDetail(ApprovalFlow flow) {
        ApprovalFlowDetail detail = new ApprovalFlowDetail();
        detail.setId(flow.getId());
        detail.setNodeLevel(flow.getNodeLevel());
        detail.setNodeName(flow.getNodeName());
        detail.setApproverId(flow.getApproverId());
        detail.setApproverName(flow.getApproverName());
        detail.setStatus(flow.getStatus());
        detail.setStatusDesc(ApprovalStatus.fromCode(flow.getStatus()).getDesc());
        detail.setComment(flow.getComment());
        detail.setCreatedAt(flow.getCreatedAt());
        detail.setUpdatedAt(flow.getUpdatedAt());
        return detail;
    }

    public List<RegistrationDetail> getAllRegistrations() {
        List<Registration> registrations = registrationRepository.findAll();
        return registrations.stream()
                .map(r -> getRegistrationDetail(r.getId()))
                .filter(d -> d != null)
                .collect(Collectors.toList());
    }

    public List<RegistrationDetail> getRegistrationsByStatus(Integer status) {
        List<Registration> registrations = registrationRepository.findByStatus(status);
        return registrations.stream()
                .map(r -> getRegistrationDetail(r.getId()))
                .filter(d -> d != null)
                .collect(Collectors.toList());
    }

    public List<RegistrationDetail> getRegistrationsByCheckPass(Integer checkPass) {
        List<Registration> registrations = registrationRepository.findByCheckPass(checkPass);
        return registrations.stream()
                .map(r -> getRegistrationDetail(r.getId()))
                .filter(d -> d != null)
                .collect(Collectors.toList());
    }

    public List<RegistrationDetail> getPendingApprovals(Integer nodeLevel) {
        List<Registration> registrations = registrationRepository.findByStatusAndCurrentApprovalNode(
                ApprovalStatus.PENDING.getCode(), nodeLevel);
        return registrations.stream()
                .map(r -> getRegistrationDetail(r.getId()))
                .filter(d -> d != null)
                .collect(Collectors.toList());
    }
}