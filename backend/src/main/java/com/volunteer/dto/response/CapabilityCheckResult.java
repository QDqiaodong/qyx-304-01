package com.volunteer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityCheckResult {

    private Boolean pass;

    private List<String> skillCheck;

    private List<String> certCheck;

    private String hoursCheck;

    /** 活动时效校验明细，例如「活动时效 [进行中] - 通过」 */
    private String activityCheck;

    /** 活动是否已散场（endTime 已过）：散场后新报名与通过都不成立 */
    private Boolean activityEnded;

    /** 是否存在已过有效期的必备证书 */
    private Boolean certExpired;

    private String message;
}