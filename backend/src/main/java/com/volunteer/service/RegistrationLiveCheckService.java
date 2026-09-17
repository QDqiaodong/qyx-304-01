package com.volunteer.service;

import com.alibaba.fastjson.JSON;
import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Activity;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 报名单现场复核落账的唯一口径：岗位门槛、证书时效、活动时效都在持锁事务内现场判。
 *
 * 调用方必须已按约定顺序持锁（活动 → 岗位 → 报名，或门槛重检的岗位 → 报名）。
 * 结局与门槛收紧重检一致：
 * - 复核失败：在途单停在能力校验失败（记住被卡前节点）、立即让出名额，通过动作不成立；
 *   已批完（含组长已通过）的人保留审批结果与节点，仅以 recheckPass=0 让出满员名额；
 * - 复核通过：此前被卡在能力校验失败的在途单恢复到被卡前节点重新占编。
 */
@Service
public class RegistrationLiveCheckService {

    @Autowired
    private CapabilityValidationService capabilityValidationService;

    /**
     * 按现场数据复核并把结论写到报名单上（不落库，由调用方 save）。
     *
     * @param lockedActivity 调用方已行锁持有的活动；为 null 时现场读取（门槛/证书扫描路径）
     */
    public CapabilityCheckResult settle(Registration registration, Position position, Activity lockedActivity) {
        CapabilityCheckResult result = lockedActivity != null
                ? capabilityValidationService.validateAgainstContext(
                        registration.getVolunteerId(), position, lockedActivity)
                : capabilityValidationService.validateAgainstPosition(
                        registration.getVolunteerId(), position);

        registration.setRecheckResult(JSON.toJSONString(result));
        registration.setRecheckPass(Boolean.TRUE.equals(result.getPass()) ? 1 : 0);

        Integer status = registration.getStatus();
        if (Boolean.TRUE.equals(result.getPass())) {
            // 此前因门槛收紧/证件过期/活动散场被卡的在途单，现场恢复通过后回到被卡前节点
            if (ApprovalStatus.CHECK_FAILED.getCode().equals(status)
                    && registration.getResumeNode() != null) {
                registration.setStatus(ApprovalStatus.PENDING.getCode());
                registration.setCurrentApprovalNode(registration.getResumeNode());
                registration.setResumeNode(null);
            }
            return result;
        }

        if (ApprovalStatus.COMPLETED.getCode().equals(status)
                || ApprovalStatus.APPROVED.getCode().equals(status)) {
            // 已批完/组长已通过的人不清退：保留审批结果与节点，仅靠 recheckPass=0 让出名额
            return result;
        }

        // 在途单（含此前已被卡的）：停在能力校验失败，通过动作直接不成立
        if (!ApprovalStatus.CHECK_FAILED.getCode().equals(status)) {
            registration.setResumeNode(registration.getCurrentApprovalNode());
        }
        registration.setStatus(ApprovalStatus.CHECK_FAILED.getCode());
        registration.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
        return result;
    }

    /** 健康在途单（无复核结论、未被卡）无需落账，避免扫描空刷 updatedAt。 */
    public boolean needsSettle(Registration registration, boolean livePass) {
        if (!livePass) {
            return true;
        }
        if (ApprovalStatus.CHECK_FAILED.getCode().equals(registration.getStatus())
                && registration.getResumeNode() != null) {
            return true;
        }
        return registration.getRecheckPass() != null && registration.getRecheckPass() == 0;
    }
}
