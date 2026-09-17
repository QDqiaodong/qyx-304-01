package com.volunteer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审批动作结果：success 为 false 时通过/驳回/退回均未落账 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalActionResult {

    private boolean success;

    private String message;

    public static ApprovalActionResult ok(String message) {
        return new ApprovalActionResult(true, message);
    }

    public static ApprovalActionResult fail(String message) {
        return new ApprovalActionResult(false, message);
    }
}
