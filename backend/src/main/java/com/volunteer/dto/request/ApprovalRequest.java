package com.volunteer.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    private Long registrationId;

    private Integer action;

    private String comment;

    private Long approverId;

    private String approverName;
}