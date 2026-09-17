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
import com.volunteer.repository.ActivityRepository;
import com.volunteer.repository.ApprovalFlowRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
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
    private RegistrationLiveCheckService registrationLiveCheckService;

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
     * 加锁顺序：先活动行（FOR UPDATE，与散场扫描/新报名互斥），再岗位行（与门槛写入互斥），
     * 最后报名行。「通过」在持锁状态下按最新现场（门槛 + 证书有效期 + 活动时效）重检：
     *   - 护理资格证昨天到期、活动已散场等：重检失败，通过不成立，在途单停校验并让出名额；
     *   - 通过发生时证件仍在有效期、活动未散场且门槛仍能过：通过成立。
     * 与证书到期改写、门槛写入并发时账上只出现一种结局，不会出现不合格的人已显示批完。
     */
    @Transactional
    public ApprovalActionResult approve(ApprovalRequest request) {
        Registration unlocked = registrationRepository.findById(request.getRegistrationId()).orElse(null);
        if (unlocked == null) {
            return ApprovalActionResult.fail("报名单不存在");
        }

        // 先活动、再岗位、最后报名 —— 与散场扫描、报名、门槛写入同一顺序，杜绝互锁
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

        // 退回修改未重提、门槛收紧/证件过期/活动散场后能力校验失败的单子，不允许继续任何审批
        if (ApprovalStatus.RETURNED.getCode().equals(registration.getStatus())
                || ApprovalStatus.CHECK_FAILED.getCode().equals(registration.getStatus())) {
            return ApprovalActionResult.fail("报名单未通过最新校验（门槛/证件时效/活动时效），无法审批");
        }

        // 通过动作：持锁按最新现场（门槛 + 证件有效期 + 活动时效）重检，任一不过则通过直接不成立
        if (action == 1) {
            CapabilityCheckResult liveCheck = capabilityValidationService.validateAgainstContext(
                    registration.getVolunteerId(), position, activity);
            if (!Boolean.TRUE.equals(liveCheck.getPass())) {
                // 与门槛收紧/散场清场对齐：记复核失败，在途单停在能力校验失败并让出名额
                registrationLiveCheckService.settle(registration, position, activity);
                registrationRepository.save(registration);
                return ApprovalActionResult.fail(liveCheck.getMessage() + "，审批不成立");
            }
            // 通过时门槛仍能过：若曾有失败复核结论，纠正为通过
            if (registration.getRecheckPass() != null && registration.getRecheckPass() == 0) {
                registration.setRecheckPass(1);
                registration.setRecheckResult(com.alibaba.fastjson.JSON.toJSONString(liveCheck));
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
