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

    private String message;
}