package com.volunteer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalFlowDetail {

    private Long id;

    private Integer nodeLevel;

    private String nodeName;

    private Long approverId;

    private String approverName;

    private Integer status;

    private String statusDesc;

    private String comment;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}