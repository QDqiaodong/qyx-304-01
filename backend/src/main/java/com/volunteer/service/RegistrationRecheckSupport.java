package com.volunteer.service;

import com.alibaba.fastjson.JSON;
import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.enums.BlockReason;
import org.springframework.stereotype.Component;

/**
 * 报名单在「岗位门槛 / 证件时效 / 活动在办」三道闸门下的状态流转统一口径。
 *
 * 入参均为调用方事务内已加行锁读出的最新数据，调用顺序由各 Service 约定，
 * 保证并发提交时账上只出现一种结局。
 */
@Component
public class RegistrationRecheckSupport {

    /**
     * 现场复核结论落账。
     *
     * @param registration    行锁持有的报名单
     * @param result          按当前门槛 + 今日证件时效复核的结论
     * @param activityOngoing 报名所属活动此刻是否仍在办
     */
    public void applyLiveOutcome(Registration registration,
                                 CapabilityCheckResult result,
                                 boolean activityOngoing) {
        boolean pass = Boolean.TRUE.equals(result.getPass()) && activityOngoing;

        registration.setRecheckResult(JSON.toJSONString(result));
        registration.setRecheckPass(pass ? 1 : 0);

        Integer status = registration.getStatus();
        boolean finished = ApprovalStatus.COMPLETED.getCode().equals(status)
                || ApprovalStatus.APPROVED.getCode().equals(status);

        if (pass) {
            // 已批完的人复核通过：审批结果不动，继续占编，清掉阻塞原因
            registration.setBlockReason(null);
            return;
        }

        if (finished) {
            // 已经批完的人不清退：历史审批结果与节点保留，仅靠 recheckPass=0 让出满员名额
            registration.setBlockReason(resolveReason(result, activityOngoing));
            return;
        }

        // 在途单：停在能力校验失败，通过动作直接不成立，记住被卡前节点
        if (!ApprovalStatus.CHECK_FAILED.getCode().equals(status)) {
            registration.setResumeNode(registration.getCurrentApprovalNode());
        }
        registration.setStatus(ApprovalStatus.CHECK_FAILED.getCode());
        registration.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
        registration.setBlockReason(resolveReason(result, activityOngoing));
    }

    /**
     * 条件解除后尝试恢复在途单：只有「造成阻塞的那一道闸门」解除、
     * 现场复核全过且活动仍在办，才回到被卡前节点重新占编。
     *
     * @param clearedReason 本次提交所解除的闸门；null 表示无条件复核（旧逻辑）
     */
    public void resumeIfCleared(Registration registration,
                                CapabilityCheckResult result,
                                boolean activityOngoing,
                                BlockReason clearedReason) {
        Integer status = registration.getStatus();
        boolean blockedHere = ApprovalStatus.CHECK_FAILED.getCode().equals(status);

        if (!blockedHere || !Boolean.TRUE.equals(result.getPass()) || !activityOngoing) {
            // 仍过不了：按最新结论落账（原因可能切换，例如门槛放宽了但证书过期）
            applyLiveOutcome(registration, result, activityOngoing);
            return;
        }

        String reason = registration.getBlockReason();
        if (clearedReason != null && reason != null
                && !BlockReason.NONE.name().equals(reason)
                && !reason.equals(clearedReason.name())) {
            // 是被另一道闸门卡住的，本次改动无权放行
            registration.setRecheckResult(JSON.toJSONString(result));
            registration.setRecheckPass(0);
            return;
        }

        registration.setRecheckResult(JSON.toJSONString(result));
        registration.setRecheckPass(1);
        registration.setBlockReason(null);
        registration.setStatus(ApprovalStatus.PENDING.getCode());
        registration.setCurrentApprovalNode(registration.getResumeNode() == null
                ? ApprovalNode.LEADER.getLevel() : registration.getResumeNode());
        registration.setResumeNode(null);
    }

    /** 失败原因归类：活动散场优先，其次证件过期，最后归到岗位门槛 */
    private String resolveReason(CapabilityCheckResult result, boolean activityOngoing) {
        if (!activityOngoing) {
            return BlockReason.ACTIVITY.name();
        }
        if (result.getExpiredCertificates() != null && !result.getExpiredCertificates().isEmpty()) {
            return BlockReason.CERT.name();
        }
        return BlockReason.THRESHOLD.name();
    }
}
