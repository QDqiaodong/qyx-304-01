package com.volunteer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "registrations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "volunteer_id", nullable = false)
    private Long volunteerId;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "apply_message", columnDefinition = "TEXT")
    private String applyMessage;

    @Column(name = "status")
    private Integer status;

    @Column(name = "capability_check_result", columnDefinition = "TEXT")
    private String capabilityCheckResult;

    @Column(name = "check_pass")
    private Integer checkPass;

    /** 报名当时所对的岗位门槛版本 */
    @Column(name = "requirement_version_at_apply")
    private Integer requirementVersionAtApply;

    /** 岗位门槛改完后的最新复核结果（JSON）；null 表示尚未复核，仍以报名时校验为准 */
    @Column(name = "recheck_result", columnDefinition = "TEXT")
    private String recheckResult;

    /** 最新复核是否通过：1 通过 / 0 失败 / null 未复核 */
    @Column(name = "recheck_pass")
    private Integer recheckPass;

    /** 因门槛收紧被卡在能力校验前，恢复时回到的原审批节点 */
    @Column(name = "resume_node")
    private Integer resumeNode;

    @Column(name = "current_approval_node")
    private Integer currentApprovalNode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = 0;
        }
        if (checkPass == null) {
            checkPass = 0;
        }
        if (currentApprovalNode == null) {
            currentApprovalNode = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}