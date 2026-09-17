package com.volunteer.service;

import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Activity;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.repository.ActivityRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 活动在办闸门：散场后在途单通过失败、已批完不计数；重新开启后被活动闸门卡住的单恢复。
 */
@ExtendWith(MockitoExtension.class)
class ActivityGateRecheckTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private RegistrationRepository registrationRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private CapabilityValidationService capabilityValidationService;
    @Spy private RegistrationRecheckSupport recheckSupport = new RegistrationRecheckSupport();

    @InjectMocks
    private ActivityService activityService;

    private Activity activity;
    private Position position;

    @BeforeEach
    void setUp() {
        activity = new Activity();
        activity.setId(2L);
        activity.setName("敬老院关爱行动");
        activity.setStartTime(LocalDateTime.now().minusDays(2));
        activity.setEndTime(LocalDateTime.now().plusDays(1));
        activity.setStatus(1);

        position = new Position();
        position.setId(10L);
        position.setActivityId(2L);
        position.setRequiredCertificates("护理资格证");
    }

    private Registration inflight() {
        Registration r = new Registration();
        r.setId(100L);
        r.setVolunteerId(5L);
        r.setActivityId(2L);
        r.setPositionId(10L);
        r.setStatus(ApprovalStatus.PENDING.getCode());
        r.setCurrentApprovalNode(ApprovalNode.LEADER.getLevel());
        r.setCheckPass(1);
        return r;
    }

    private CapabilityCheckResult pass() {
        CapabilityCheckResult result = new CapabilityCheckResult();
        result.setPass(true);
        return result;
    }

    @Test
    void ending_activity_blocks_inflight_and_excludes_completed_from_roster() {
        Registration inflight = inflight();
        Registration completed = inflight();
        completed.setId(101L);
        completed.setStatus(ApprovalStatus.COMPLETED.getCode());
        completed.setCurrentApprovalNode(ApprovalNode.COMPLETED.getLevel());

        when(activityRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activity));
        when(activityRepository.save(any(Activity.class))).thenAnswer(i -> i.getArgument(0));
        when(registrationRepository.findByActivityIdAndStatusInForUpdate(eq(2L), any()))
                .thenReturn(List.of(inflight, completed));
        when(positionRepository.findById(10L)).thenReturn(Optional.of(position));
        when(capabilityValidationService.validateAgainstPosition(eq(5L), eq(position), any(LocalDate.class), eq(true)))
                .thenReturn(pass());

        // 活动散场：状态置结束
        Activity input = new Activity();
        input.setName(activity.getName());
        input.setStartTime(activity.getStartTime());
        input.setEndTime(activity.getEndTime());
        input.setStatus(0);
        activityService.updateActivity(2L, input);

        // 在途单：通过失败、停住、让出名额
        assertEquals(ApprovalStatus.CHECK_FAILED.getCode(), inflight.getStatus());
        assertEquals("ACTIVITY", inflight.getBlockReason());
        assertEquals(ApprovalNode.LEADER.getLevel(), inflight.getResumeNode());
        assertEquals(0, inflight.getRecheckPass());

        // 已批完：审批历史保留，但不再占编
        assertEquals(ApprovalStatus.COMPLETED.getCode(), completed.getStatus());
        assertEquals(0, completed.getRecheckPass());
    }

    @Test
    void reopening_activity_resumes_activity_blocked_inflight() {
        activity.setStatus(0);
        activity.setEndTime(LocalDateTime.now().minusHours(1));

        Registration blocked = inflight();
        blocked.setStatus(ApprovalStatus.CHECK_FAILED.getCode());
        blocked.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
        blocked.setResumeNode(ApprovalNode.LEADER.getLevel());
        blocked.setBlockReason("ACTIVITY");
        blocked.setRecheckPass(0);

        when(activityRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activity));
        when(activityRepository.save(any(Activity.class))).thenAnswer(i -> i.getArgument(0));
        when(registrationRepository.findByActivityIdAndStatusInForUpdate(eq(2L), any()))
                .thenReturn(List.of(blocked));
        when(positionRepository.findById(10L)).thenReturn(Optional.of(position));
        when(capabilityValidationService.validateAgainstPosition(eq(5L), eq(position), any(LocalDate.class), eq(true)))
                .thenReturn(pass());

        Activity input = new Activity();
        input.setName(activity.getName());
        input.setStartTime(activity.getStartTime());
        // 延期到未来并恢复进行中
        input.setEndTime(LocalDateTime.now().plusDays(3));
        input.setStatus(1);
        activityService.updateActivity(2L, input);

        assertEquals(ApprovalStatus.PENDING.getCode(), blocked.getStatus());
        assertEquals(ApprovalNode.LEADER.getLevel(), blocked.getCurrentApprovalNode());
        assertEquals(1, blocked.getRecheckPass());
        assertEquals(null, blocked.getBlockReason());
    }

    @Test
    void cert_blocked_registration_is_not_resumed_by_activity_reopen() {
        // 被证件闸门卡住的单，活动延期/重开无权放行
        Registration blocked = inflight();
        blocked.setStatus(ApprovalStatus.CHECK_FAILED.getCode());
        blocked.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
        blocked.setResumeNode(ApprovalNode.LEADER.getLevel());
        blocked.setBlockReason("CERT");
        blocked.setRecheckPass(0);

        when(activityRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activity));
        when(activityRepository.save(any(Activity.class))).thenAnswer(i -> i.getArgument(0));
        when(registrationRepository.findByActivityIdAndStatusInForUpdate(eq(2L), any()))
                .thenReturn(List.of(blocked));
        when(positionRepository.findById(10L)).thenReturn(Optional.of(position));
        when(capabilityValidationService.validateAgainstPosition(eq(5L), eq(position), any(LocalDate.class), eq(true)))
                .thenReturn(pass());

        activity.setStatus(0);
        Activity input = new Activity();
        input.setName(activity.getName());
        input.setStartTime(activity.getStartTime());
        input.setEndTime(LocalDateTime.now().plusDays(3));
        input.setStatus(1);
        activityService.updateActivity(2L, input);

        assertEquals(ApprovalStatus.CHECK_FAILED.getCode(), blocked.getStatus());
        assertEquals("CERT", blocked.getBlockReason());
    }
}
