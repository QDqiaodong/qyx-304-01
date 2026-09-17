package com.volunteer.service;

import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Activity;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.repository.ActivityRepository;
import com.volunteer.repository.ApprovalFlowRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import com.volunteer.repository.VolunteerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退回修改后再送审：能力校验和两个审批节点从头来，不许接回退回前节点；
 * 非退回状态不许重提。
 */
@ExtendWith(MockitoExtension.class)
class RegistrationResubmitTest {

    @Mock private RegistrationRepository registrationRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private VolunteerRepository volunteerRepository;
    @Mock private ApprovalFlowRepository approvalFlowRepository;
    @Mock private CapabilityValidationService capabilityValidationService;
    @Mock private ApprovalFlowService approvalFlowService;
    @Mock private PositionCacheService positionCacheService;

    @InjectMocks
    private RegistrationService registrationService;

    private Position position;
    private Activity activity;
    private Registration returned;

    @BeforeEach
    void setUp() {
        position = new Position();
        position.setId(10L);
        position.setRequirementVersion(3);
        position.setRequiredCertificates("急救证");

        activity = new Activity();
        activity.setId(2L);
        activity.setStatus(1);
        activity.setEndTime(java.time.LocalDateTime.now().plusDays(1));

        returned = new Registration();
        returned.setId(100L);
        returned.setVolunteerId(5L);
        returned.setActivityId(2L);
        returned.setPositionId(10L);
        returned.setStatus(ApprovalStatus.RETURNED.getCode());
        returned.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
        returned.setCheckPass(1);
    }

    private CapabilityCheckResult pass() {
        CapabilityCheckResult r = new CapabilityCheckResult();
        r.setPass(true);
        return r;
    }

    private CapabilityCheckResult fail() {
        CapabilityCheckResult r = new CapabilityCheckResult();
        r.setPass(false);
        return r;
    }

    private void stubLocks() {
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(returned));
        when(activityRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activity));
        when(positionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(position));
        when(registrationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(returned));
    }

    private void stubLocksUpToActivity() {
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(returned));
        when(activityRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activity));
    }

    @Test
    void resubmit_passing_rebuilds_flows_from_leader_and_marks_pending() {
        stubLocks();
        when(capabilityValidationService.validateAgainstContext(5L, position, activity)).thenReturn(pass());
        when(registrationRepository.save(returned)).thenReturn(returned);
        when(approvalFlowRepository.findByRegistrationIdOrderByNodeLevelAsc(100L)).thenReturn(java.util.List.of());

        registrationService.resubmit(100L, "补了备注");

        // 旧节点全删，绝不接回退回前的负责人节点
        verify(approvalFlowRepository).deleteByRegistrationId(100L);
        verify(approvalFlowService).createApprovalFlow(100L);
        assertEquals(ApprovalStatus.PENDING.getCode(), returned.getStatus());
        assertEquals(ApprovalNode.LEADER.getLevel(), returned.getCurrentApprovalNode());
        assertEquals(3, returned.getRequirementVersionAtApply());
        // 重提是全新校验，旧复核结论清空
        assertNullField(returned);
    }

    private void assertNullField(Registration r) {
        org.junit.jupiter.api.Assertions.assertNull(r.getRecheckPass());
        org.junit.jupiter.api.Assertions.assertNull(r.getRecheckResult());
    }

    @Test
    void resubmit_failing_is_rejected_and_creates_no_flow() {
        stubLocks();
        when(capabilityValidationService.validateAgainstContext(5L, position, activity)).thenReturn(fail());
        when(registrationRepository.save(returned)).thenReturn(returned);
        when(approvalFlowRepository.findByRegistrationIdOrderByNodeLevelAsc(100L)).thenReturn(java.util.List.of());

        registrationService.resubmit(100L, null);

        // 重提即按当前门槛重新报名，不通过走驳回，且不占名额
        assertEquals(ApprovalStatus.REJECTED.getCode(), returned.getStatus());
        assertEquals(ApprovalNode.CAPABILITY_CHECK.getLevel(), returned.getCurrentApprovalNode());
        verify(approvalFlowRepository).deleteByRegistrationId(100L);
        verify(approvalFlowService, never()).createApprovalFlow(anyLong());
    }

    @Test
    void resubmit_after_activity_ended_is_rejected() {
        activity.setEndTime(java.time.LocalDateTime.now().minusMinutes(1));
        stubLocksUpToActivity();
        when(capabilityValidationService.isActivityEnded(activity)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> registrationService.resubmit(100L, null));
        verify(approvalFlowRepository, never()).deleteByRegistrationId(anyLong());
    }

    @Test
    void non_returned_registration_cannot_resubmit() {
        returned.setStatus(ApprovalStatus.PENDING.getCode());
        stubLocks();

        assertThrows(IllegalStateException.class, () -> registrationService.resubmit(100L, null));
        verify(approvalFlowRepository, never()).deleteByRegistrationId(anyLong());
    }
}
