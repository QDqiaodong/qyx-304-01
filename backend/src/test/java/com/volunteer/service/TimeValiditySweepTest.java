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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 排班口说了算：活动散场、证件到期立即清场腾位，不等岗位页的人拖到活动当晚。
 * 在途单停能力校验失败、通过不成立；已批完的保留历史记录但不再占满员。
 */
@ExtendWith(MockitoExtension.class)
class TimeValiditySweepTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private RegistrationRepository registrationRepository;
    @Mock private CapabilityValidationService capabilityValidationService;

    @InjectMocks
    private TimeValiditySweepExecutor executor;

    private RegistrationLiveCheckService liveCheck;

    private Position position;
    private Activity activity;

    @BeforeEach
    void setUp() {
        liveCheck = new RegistrationLiveCheckService();
        ReflectionTestUtils.setField(liveCheck, "capabilityValidationService", capabilityValidationService);
        ReflectionTestUtils.setField(executor, "registrationLiveCheckService", liveCheck);

        position = new Position();
        position.setId(10L);
        position.setActivityId(2L);
        position.setRequiredCertificates("护理资格证");
        position.setRequirementVersion(1);

        activity = new Activity();
        activity.setId(2L);
        activity.setStatus(1);
        activity.setEndTime(LocalDateTime.now().minusHours(1));
    }

    private Registration reg(long id, int status, int node) {
        Registration r = new Registration();
        r.setId(id);
        r.setVolunteerId(id);
        r.setActivityId(2L);
        r.setPositionId(10L);
        r.setCheckPass(1);
        r.setStatus(status);
        r.setCurrentApprovalNode(node);
        return r;
    }

    private CapabilityCheckResult fail() {
        CapabilityCheckResult r = new CapabilityCheckResult();
        r.setPass(false);
        return r;
    }

    private CapabilityCheckResult pass() {
        CapabilityCheckResult r = new CapabilityCheckResult();
        r.setPass(true);
        return r;
    }

    @Test
    void ended_activity_sweep_stops_inflight_and_keeps_completed_history_but_unoccupies() {
        Registration inflight = reg(1L, ApprovalStatus.PENDING.getCode(), ApprovalNode.LEADER.getLevel());
        Registration completed = reg(2L, ApprovalStatus.COMPLETED.getCode(), ApprovalNode.COMPLETED.getLevel());

        when(activityRepository.findEndedActivities()).thenReturn(List.of(activity));
        when(activityRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activity));
        when(capabilityValidationService.isActivityEnded(activity)).thenReturn(true);
        when(registrationRepository.findByActivityIdAndStatusInForUpdate(any(), any()))
                .thenReturn(List.of(inflight, completed));
        when(positionRepository.findById(10L)).thenReturn(Optional.of(position));
        when(capabilityValidationService.validateAgainstContext(any(), any(), any())).thenReturn(fail());

        executor.sweepEndedActivities();

        // 在途单：停在能力校验失败、记住组长节点、让出名额
        assertEquals(ApprovalStatus.CHECK_FAILED.getCode(), inflight.getStatus());
        assertEquals(0, inflight.getRecheckPass());
        assertEquals(ApprovalNode.LEADER.getLevel(), inflight.getResumeNode());

        // 已批完：历史审批记录与状态保留，但复核失败不占满员
        assertEquals(ApprovalStatus.COMPLETED.getCode(), completed.getStatus());
        assertEquals(ApprovalNode.COMPLETED.getLevel(), completed.getCurrentApprovalNode());
        assertEquals(0, completed.getRecheckPass());
    }

    @Test
    void ongoing_activity_is_not_swept_even_if_candidate_list_stale() {
        when(activityRepository.findEndedActivities()).thenReturn(List.of(activity));
        when(activityRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activity));
        when(capabilityValidationService.isActivityEnded(activity)).thenReturn(false);

        executor.sweepEndedActivities();

        verify(registrationRepository, never()).findByActivityIdAndStatusInForUpdate(any(), any());
    }

    @Test
    void expired_certificate_sweep_stops_inflight_and_frees_slot() {
        Registration inflight = reg(1L, ApprovalStatus.PENDING.getCode(), ApprovalNode.MANAGER.getLevel());
        Registration healthy = reg(3L, ApprovalStatus.PENDING.getCode(), ApprovalNode.LEADER.getLevel());

        when(positionRepository.findAll()).thenReturn(List.of(position));
        when(registrationRepository.findByPositionIdAndStatusInForUpdate(any(), any()))
                .thenReturn(List.of(inflight, healthy));
        when(capabilityValidationService.validateAgainstPosition(1L, position)).thenReturn(fail());
        when(capabilityValidationService.validateAgainstPosition(3L, position)).thenReturn(pass());

        executor.sweepExpiredCertificates();

        // 过期的在途单：停在能力校验失败（记住被卡前的负责人节点）、让出名额
        assertEquals(ApprovalStatus.CHECK_FAILED.getCode(), inflight.getStatus());
        assertEquals(0, inflight.getRecheckPass());
        assertEquals(ApprovalNode.MANAGER.getLevel(), inflight.getResumeNode());

        // 现场仍通过的健康单：不刷结论、不动状态
        assertEquals(ApprovalStatus.PENDING.getCode(), healthy.getStatus());
        assertEquals(null, healthy.getRecheckPass());
        verify(registrationRepository, never()).save(healthy);
    }

    @Test
    void expired_certificate_sweep_unoccupies_already_completed_without_clearing_history() {
        Registration completed = reg(2L, ApprovalStatus.COMPLETED.getCode(), ApprovalNode.COMPLETED.getLevel());

        when(positionRepository.findAll()).thenReturn(List.of(position));
        when(registrationRepository.findByPositionIdAndStatusInForUpdate(any(), any()))
                .thenReturn(List.of(completed));
        when(capabilityValidationService.validateAgainstPosition(2L, position)).thenReturn(fail());

        executor.sweepExpiredCertificates();

        assertEquals(ApprovalStatus.COMPLETED.getCode(), completed.getStatus());
        assertEquals(0, completed.getRecheckPass());
    }

    @Test
    void certificate_edit_immediately_settles_volunteer_registrations() {
        Registration inflight = reg(1L, ApprovalStatus.PENDING.getCode(), ApprovalNode.LEADER.getLevel());

        when(positionRepository.findAll()).thenReturn(List.of(position));
        when(positionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(position));
        when(registrationRepository.findByVolunteerIdAndPositionIdAndStatusInForUpdate(any(), any(), any()))
                .thenReturn(List.of(inflight));
        when(capabilityValidationService.validateAgainstPosition(1L, position)).thenReturn(fail());

        executor.settleVolunteerCertificates(1L);

        // 改有效期与扫描同一口径：过期立即停校验、让出名额，不用等 30 秒定时任务
        assertEquals(ApprovalStatus.CHECK_FAILED.getCode(), inflight.getStatus());
        assertEquals(0, inflight.getRecheckPass());
    }

    @Test
    void activity_edit_to_ended_immediately_settles() {
        Registration inflight = reg(1L, ApprovalStatus.PENDING.getCode(), ApprovalNode.LEADER.getLevel());

        when(activityRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activity));
        when(capabilityValidationService.isActivityEnded(activity)).thenReturn(true);
        when(registrationRepository.findByActivityIdAndStatusInForUpdate(any(), any()))
                .thenReturn(List.of(inflight));
        when(positionRepository.findById(10L)).thenReturn(Optional.of(position));
        when(capabilityValidationService.validateAgainstContext(any(), any(), any())).thenReturn(fail());

        executor.settleActivity(2L);

        assertEquals(ApprovalStatus.CHECK_FAILED.getCode(), inflight.getStatus());
        assertEquals(0, inflight.getRecheckPass());
    }
}
