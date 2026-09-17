package com.volunteer.service;

import com.volunteer.dto.request.ApprovalRequest;
import com.volunteer.dto.response.ApprovalActionResult;
import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.ApprovalFlow;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.repository.ApprovalFlowRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门槛写入与通过动作并发时账上只许一种结局：
 * 重检已失败则通过不成立；通过发生时门槛仍能过才成立。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalPassGuardTest {

    @Mock
    private ApprovalFlowRepository approvalFlowRepository;
    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private CapabilityValidationService capabilityValidationService;

    @InjectMocks
    private ApprovalFlowService approvalFlowService;

    private Position position;
    private Registration registration;
    private ApprovalRequest passRequest;

    @BeforeEach
    void setUp() {
        position = new Position();
        position.setId(10L);
        position.setRequiredCertificates("急救证");
        position.setRequirementVersion(2);

        registration = new Registration();
        registration.setId(100L);
        registration.setVolunteerId(5L);
        registration.setPositionId(10L);
        registration.setStatus(ApprovalStatus.PENDING.getCode());
        registration.setCurrentApprovalNode(ApprovalNode.LEADER.getLevel());
        registration.setCheckPass(1);

        passRequest = new ApprovalRequest();
        passRequest.setRegistrationId(100L);
        passRequest.setAction(1);
        passRequest.setApproverId(1L);
        passRequest.setApproverName("组长");
    }

    @Test
    void pass_fails_when_live_check_against_latest_threshold_fails() {
        when(positionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(position));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(registration));
        when(registrationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(registration));
        CapabilityCheckResult fail = new CapabilityCheckResult();
        fail.setPass(false);
        when(capabilityValidationService.validateAgainstPosition(5L, position)).thenReturn(fail);

        ApprovalActionResult result = approvalFlowService.approve(passRequest);

        assertFalse(result.isSuccess());
        // 单停在能力校验失败并让出名额
        assertEquals(ApprovalStatus.CHECK_FAILED.getCode(), registration.getStatus());
        assertEquals(0, registration.getRecheckPass());
        assertEquals(ApprovalNode.CAPABILITY_CHECK.getLevel(), registration.getCurrentApprovalNode());
        // 绝不允许组长节点被置为通过
        verify(approvalFlowRepository, never()).save(any(ApprovalFlow.class));
    }

    @Test
    void pass_succeeds_when_live_check_still_passes() {
        ApprovalFlow leaderFlow = new ApprovalFlow();
        leaderFlow.setNodeLevel(ApprovalNode.LEADER.getLevel());
        leaderFlow.setStatus(ApprovalStatus.PENDING.getCode());

        when(positionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(position));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(registration));
        when(registrationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(registration));
        CapabilityCheckResult pass = new CapabilityCheckResult();
        pass.setPass(true);
        when(capabilityValidationService.validateAgainstPosition(5L, position)).thenReturn(pass);
        when(approvalFlowRepository.findByRegistrationId(100L)).thenReturn(List.of(leaderFlow));

        ApprovalActionResult result = approvalFlowService.approve(passRequest);

        assertTrue(result.isSuccess());
        assertEquals(ApprovalStatus.APPROVED.getCode(), leaderFlow.getStatus());
        // 进入负责人节点
        assertEquals(ApprovalNode.MANAGER.getLevel(), registration.getCurrentApprovalNode());
    }

    @Test
    void returned_registration_cannot_be_approved_before_resubmit() {
        registration.setStatus(ApprovalStatus.RETURNED.getCode());
        when(positionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(position));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(registration));
        when(registrationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(registration));

        ApprovalActionResult result = approvalFlowService.approve(passRequest);

        assertFalse(result.isSuccess());
        verify(approvalFlowRepository, never()).save(any(ApprovalFlow.class));
    }
}
