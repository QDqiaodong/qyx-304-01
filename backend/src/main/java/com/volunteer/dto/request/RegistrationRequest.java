package com.volunteer.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationRequest {

    private Long volunteerId;

    private Long activityId;

    private Long positionId;

    private String applyMessage;
}