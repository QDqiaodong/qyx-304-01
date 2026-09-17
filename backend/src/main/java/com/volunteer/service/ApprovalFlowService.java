package com.volunteer.service;

import com.volunteer.dto.request.ApprovalRequest;
import com.volunteer.dto.response.ApprovalActionResult;
import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Activity;
import com.volunteer.entity.ApprovalFlow;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.enums.BlockReason;
import com.volunteer.repository.ActivityRepository;
import com.volunteer.repository.ApprovalFlowRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import com.volunteer.util.GateRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApprovalFlowService {

    @Autowired
    private ApprovalFlowRepository approvalFlowRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private CapabilityValidationService capabilityValidationService;

    @Autowired
    private RegistrationRecheckSupport recheckSupport;

    @Transactional
    public void createApprovalFlow(Long registrationId) {
        for (ApprovalNode node : ApprovalNode.values()) {
            if (node == ApprovalNode.COMPLETED) {
                continue;
            }
            ApprovalFlow flow = new ApprovalFlow();
            flow.setRegistrationId(registrationId);
            flow.setNodeLevel(node.getLevel());
            flow.setNodeName(node.getName());
            flow.setStatus(ApprovalStatus.PENDING.getCode());
            approvalFlowRepository.save(flow);
        }
    }

    /**
     * 审批动作。
     *
     * 加锁顺序：先活动行（FOR UPDATE），再岗位行，再报名行，最后所需证件行。
     * 与岗位门槛写入、证件有效期写入、活动改期四处事务同序，杜绝互锁。
     * 「通过」在持锁状态下现场复核：
     *   - 活动已散场（状态结束或过了结束钟点）：通过不成立；
     *   - 岗位所需证书已过期：通过不成立；
     *   - 最新门槛复核失败：通过不成立。
     * 任一拦截都把在途单停在能力校验失败、立即让出名额；
     * 改有效期/改活动与本动作并发时，后拿锁方按最新数据落账，账上只出现一种结局。
     */
    @Transactional
    public ApprovalActionResult approve(ApprovalRequest request) {
        Registration unlocked = registrationRepository.findById(request.getRegistrationId()).orElse(null);
        if (unlocked == null) {
            return ApprovalActionResult.fail("报名单不存在");
        }

        // 先锁活动 → 岗位 → 报名，与其它写事务同一顺序
        Activity activity = activityRepository.findByIdForUpdate(unlocked.getActivityId()).orElse(null);
        if (activity == null) {
            return ApprovalActionResult.fail("活动不存在");
        }

        Position position = positionRepository.findByIdForUpdate(unlocked.getPositionId()).orElse(null);
        if (position == null) {
            return ApprovalActionResult.fail("岗位不存在");
        }

        Registration registration = registrationRepository.findByIdForUpdate(request.getRegistrationId())
                .orElse(null);
        if (registration == null) {
            return ApprovalActionResult.fail("报名单不存在");
        }

        Integer currentNode = registration.getCurrentApprovalNode();
        ApprovalNode currentApprovalNode = ApprovalNode.fromLevel(currentNode);

        if (currentApprovalNode == ApprovalNode.COMPLETED) {
            return ApprovalActionResult.fail("审批流程已结束");
        }

        Integer action = request.getAction();
        if (action == null || action < 1 || action > 3) {
            return ApprovalActionResult.fail("无效的审批动作");
        }

        // 退回修改未重提、门槛/证件/活动闸门卡住的单子，不允许继续任何审批
        if (ApprovalStatus.RETURNED.getCode().equals(registration.getStatus())
                || ApprovalStatus.CHECK_FAILED.getCode().equals(registration.getStatus())) {
            return ApprovalActionResult.fail("报名单未通过最新校验，无法审批");
        }

        // 通过动作：持锁按「活动在办 + 当前最新门槛 + 今日证件时效」现场复核
        if (action == 1) {
            if (!GateRules.isOngoing(activity)) {
                // 活动已经散场：还没批完的通过一律失败，在途单停住并让出名额
                registration.setResumeNode(registration.getCurrentApprovalNode());
                registration.setStatus(ApprovalStatus.CHECK_FAILED.getCode());
                registration.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
                registration.setRecheckPass(0);
                registration.setBlockReason(BlockReason.ACTIVITY.name());
                registrationRepository.save(registration);
                return ApprovalActionResult.fail("活动已结束，审批通过不成立");
            }

            CapabilityCheckResult liveCheck = capabilityValidationService.validateAgainstPosition(
                    registration.getVolunteerId(), position, java.time.LocalDate.now(), true);
            if (!Boolean.TRUE.equals(liveCheck.getPass())) {
                // 证件过期或门槛不过：记复核失败，在途单停在能力校验失败并让出名额
                recheckSupport.applyLiveOutcome(registration, liveCheck, true);
                registrationRepository.save(registration);
                String reason = liveCheck.getExpiredCertificates() != null
                        && !liveCheck.getExpiredCertificates().isEmpty()
                        ? "所需证书已过有效期，审批通过不成立"
                        : "岗位门槛已更新且能力校验未通过，审批不成立";
                return ApprovalActionResult.fail(reason);
            }
            // 通过时仍能过：若曾有失败复核结论，纠正为通过
            if (registration.getRecheckPass() != null && registration.getRecheckPass() == 0) {
                registration.setRecheckPass(1);
                registration.setRecheckResult(com.alibaba.fastjson.JSON.toJSONString(liveCheck));
                registration.setBlockReason(null);
            }
        }

        ApprovalFlow flow = approvalFlowRepository
                .findByRegistrationId(request.getRegistrationId())
                .stream()
                .filter(f -> f.getNodeLevel().equals(currentNode))
                .findFirst()
                .orElse(null);

        if (flow == null) {
            return ApprovalActionResult.fail("审批节点不存在");
        }

        if (action == 1) {
            flow.setStatus(ApprovalStatus.APPROVED.getCode());
            flow.setApproverId(request.getApproverId());
            flow.setApproverName(request.getApproverName());
            flow.setComment(request.getComment());
            approvalFlowRepository.save(flow);

            ApprovalNode nextNode = ApprovalNode.getNextNode(currentNode);
            if (nextNode == ApprovalNode.COMPLETED) {
                registration.setStatus(ApprovalStatus.COMPLETED.getCode());
                registration.setCurrentApprovalNode(ApprovalNode.COMPLETED.getLevel());
            } else {
                registration.setCurrentApprovalNode(nextNode.getLevel());
            }
            registrationRepository.save(registration);

            return ApprovalActionResult.ok("审批通过");
        } else if (action == 2) {
            flow.setStatus(ApprovalStatus.REJECTED.getCode());
            flow.setApproverId(request.getApproverId());
            flow.setApproverName(request.getApproverName());
            flow.setComment(request.getComment());
            approvalFlowRepository.save(flow);

            registration.setStatus(ApprovalStatus.REJECTED.getCode());
            registrationRepository.save(registration);

            return ApprovalActionResult.ok("审批驳回");
        } else {
            flow.setStatus(ApprovalStatus.RETURNED.getCode());
            flow.setApproverId(request.getApproverId());
            flow.setApproverName(request.getApproverName());
            flow.setComment(request.getComment());
            approvalFlowRepository.save(flow);

            // 退回修改：立即让出名额，重提前不再计入岗位占编；
            // 重提时能力校验与组长/负责人两个节点全部从头来
            registration.setStatus(ApprovalStatus.RETURNED.getCode());
            registration.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
            registration.setBlockReason(null);
            registrationRepository.save(registration);

            return ApprovalActionResult.ok("退回修改");
        }
    }

    public List<ApprovalFlow> getApprovalFlows(Long registrationId) {
        return approvalFlowRepository.findByRegistrationIdOrderByNodeLevelAsc(registrationId);
    }

    public ApprovalFlow getCurrentApprovalFlow(Long registrationId) {
        Registration registration = registrationRepository.findById(registrationId).orElse(null);
        if (registration == null) {
            return null;
        }
        return approvalFlowRepository
                .findByRegistrationId(registrationId)
                .stream()
                .filter(f -> f.getNodeLevel().equals(registration.getCurrentApprovalNode()))
                .findFirst()
                .orElse(null);
    }
}
