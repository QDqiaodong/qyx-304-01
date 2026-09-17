package com.volunteer.service;

import com.volunteer.dto.request.ApprovalRequest;
import com.volunteer.dto.response.ApprovalActionResult;
import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.ApprovalFlow;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
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
    private CapabilityValidationService capabilityValidationService;

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
     * 门槛写入与本方法约定加锁顺序：先岗位行（FOR UPDATE），再报名行（FOR UPDATE）。
     * 「通过」在持锁状态下按岗位最新门槛现场重检：
     *   - 重检已失败（含门槛收紧事务先提交）：通过不成立；
     *   - 通过发生时门槛仍能过：通过成立。
     * 二者并发时账上只出现一种结局，不会出现新门槛下不合格的人已显示批完。
     */
    @Transactional
    public ApprovalActionResult approve(ApprovalRequest request) {
        Registration unlocked = registrationRepository.findById(request.getRegistrationId()).orElse(null);
        if (unlocked == null) {
            return ApprovalActionResult.fail("报名单不存在");
        }

        // 先锁岗位，再锁报名 —— 与 PositionService.updatePosition 同一顺序，杜绝互锁
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

        // 退回修改未重提、门槛收紧后能力校验失败的单子，不允许继续任何审批
        if (ApprovalStatus.RETURNED.getCode().equals(registration.getStatus())
                || ApprovalStatus.CHECK_FAILED.getCode().equals(registration.getStatus())) {
            return ApprovalActionResult.fail("报名单未通过最新能力校验，无法审批");
        }

        // 通过动作：持锁按当前最新门槛现场重检，门槛改完复核失败则通过直接不成立
        if (action == 1) {
            CapabilityCheckResult liveCheck = capabilityValidationService.validateAgainstPosition(
                    registration.getVolunteerId(), position);
            if (!Boolean.TRUE.equals(liveCheck.getPass())) {
                // 与门槛收紧重检对齐：记复核失败，在途单停在能力校验失败并让出名额
                registration.setRecheckResult(com.alibaba.fastjson.JSON.toJSONString(liveCheck));
                registration.setRecheckPass(0);
                if (!ApprovalStatus.COMPLETED.getCode().equals(registration.getStatus())) {
                    registration.setResumeNode(registration.getCurrentApprovalNode());
                    registration.setStatus(ApprovalStatus.CHECK_FAILED.getCode());
                    registration.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
                }
                registrationRepository.save(registration);
                return ApprovalActionResult.fail("岗位门槛已更新且能力校验未通过，审批不成立");
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
