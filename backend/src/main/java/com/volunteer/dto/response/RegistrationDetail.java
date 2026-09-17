package com.volunteer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationDetail {

    private Long id;

    private String volunteerName;

    private String volunteerPhone;

    private String activityName;

    private String positionName;

    private String applyMessage;

    private Integer status;

    private String statusDesc;

    private Integer checkPass;

    private String checkPassDesc;

    private String capabilityCheckResult;

    /** 报名当时所对的岗位门槛版本 */
    private Integer requirementVersionAtApply;

    /** 岗位门槛改完后的最新复核结果 JSON；null 表示门槛未变、尚未复核 */
    private String recheckResult;

    /** 最新复核是否通过：1 通过 / 0 失败 / null 未复核 */
    private Integer recheckPass;

    private String recheckPassDesc;

    /** 岗位当前门槛版本，与报名时版本并排展示 */
    private Integer currentRequirementVersion;

    /** 有效校验是否通过：有复核以复核为准，无复核以报名时校验为准（决定能否占编/通过） */
    private Integer effectivePass;

    private String effectivePassDesc;

    /** 停在能力校验失败的原因：THRESHOLD 门槛 / CERT 证件过期 / ACTIVITY 活动散场 */
    private String blockReason;

    /** 所属活动此刻是否仍在办（排班实时闸门，列表据此置灰） */
    private Boolean activityOngoing;

    /** 岗位所需证书中此刻已过期的证书名称列表 */
    private List<String> expiredCertificates;

    /** 排班今晚是否可到岗：活动在办且所需证件均在有效期内 */
    private Boolean rosterEligible;

    private Integer currentApprovalNode;

    private String currentApprovalNodeDesc;

    private List<ApprovalFlowDetail> approvalFlows;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}