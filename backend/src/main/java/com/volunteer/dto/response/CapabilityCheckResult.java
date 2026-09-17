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

    /** 本次校验时已过期的岗位所需证书名称（证件时效闸门） */
    private List<String> expiredCertificates;

    private String hoursCheck;

    private String message;
}