package com.volunteer.enums;

public enum ApprovalNode {
    CAPABILITY_CHECK(0, "能力校验"),
    LEADER(1, "组长审批"),
    MANAGER(2, "负责人审批"),
    COMPLETED(3, "审批完成");

    private final Integer level;
    private final String name;

    ApprovalNode(Integer level, String name) {
        this.level = level;
        this.name = name;
    }

    public Integer getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public static ApprovalNode fromLevel(Integer level) {
        for (ApprovalNode node : values()) {
            if (node.level.equals(level)) {
                return node;
            }
        }
        return CAPABILITY_CHECK;
    }

    public static ApprovalNode getNextNode(Integer currentLevel) {
        for (ApprovalNode node : values()) {
            if (node.level.equals(currentLevel + 1)) {
                return node;
            }
        }
        return COMPLETED;
    }
}