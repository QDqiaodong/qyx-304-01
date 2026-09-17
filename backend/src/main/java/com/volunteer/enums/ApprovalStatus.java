package com.volunteer.enums;

public enum ApprovalStatus {
    PENDING(0, "待审批"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已驳回"),
    RETURNED(3, "退回修改"),
    COMPLETED(4, "审批完成"),
    CHECK_FAILED(5, "能力校验失败");

    private final Integer code;
    private final String desc;

    ApprovalStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static ApprovalStatus fromCode(Integer code) {
        for (ApprovalStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return PENDING;
    }
}