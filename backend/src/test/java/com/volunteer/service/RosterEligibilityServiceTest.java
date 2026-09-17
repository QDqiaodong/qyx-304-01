package com.volunteer.service;

import com.volunteer.entity.Activity;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.entity.VolunteerCertificate;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.repository.ActivityRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import com.volunteer.repository.VolunteerCertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 排班口实时满员口径：
 * 门槛候选集之上，活动散场或所需证件自然过期都立即减员，
 * 无需等任何人手工清理（历史审批记录保留）。
 */
@ExtendWith(MockitoExtension.class)
class RosterEligibilityServiceTest {

    @Mock private RegistrationRepository registrationRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private VolunteerCertificateRepository volunteerCertificateRepository;

    @InjectMocks
    private RosterEligibilityService rosterEligibilityService;

    private Position position;
    private Activity activity;

    private Registration candidate(long id, long volunteerId) {
        Registration r = new Registration();
        r.setId(id);
        r.setVolunteerId(volunteerId);
        r.setActivityId(2L);
        r.setPositionId(10L);
        r.setStatus(ApprovalStatus.COMPLETED.getCode());
        r.setCheckPass(1);
        return r;
    }

    private VolunteerCertificate cert(long volunteerId, String name, LocalDate expire) {
        VolunteerCertificate c = new VolunteerCertificate();
        c.setVolunteerId(volunteerId);
        c.setCertName(name);
        c.setExpireDate(expire);
        return c;
    }

    @BeforeEach
    void setUp() {
        position = new Position();
        position.setId(10L);
        position.setActivityId(2L);
        position.setRequiredCertificates("护理资格证");

        activity = new Activity();
        activity.setId(2L);
        activity.setStatus(1);
        activity.setStartTime(LocalDateTime.now().minusDays(1));
        activity.setEndTime(LocalDateTime.now().plusDays(1));
    }

    @Test
    void expired_certificate_excluded_from_roster_even_though_registration_completed() {
        Registration valid = candidate(1L, 11L);
        Registration expired = candidate(2L, 12L);

        when(registrationRepository.findOccupyingCandidatesByPositionId(10L))
                .thenReturn(List.of(valid, expired));
        when(positionRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(position));
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(activity));
        when(volunteerCertificateRepository.findByVolunteerIdAndCertNameIn(org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(cert(11L, "护理资格证", LocalDate.now().plusDays(5))));
        when(volunteerCertificateRepository.findByVolunteerIdAndCertNameIn(org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(cert(12L, "护理资格证", LocalDate.now().minusDays(1))));

        // 两条都是已批完；证件昨天到期的人今晚不计数，立即腾位
        long occupied = rosterEligibilityService.countEligible(10L);
        assertEquals(1L, occupied);
    }

    @Test
    void ended_activity_excludes_everyone_immediately() {
        Registration r1 = candidate(1L, 11L);
        Registration r2 = candidate(2L, 12L);
        activity.setStatus(1);
        activity.setEndTime(LocalDateTime.now().minusMinutes(1)); // 钟点刚过

        when(registrationRepository.findOccupyingCandidatesByPositionId(10L))
                .thenReturn(List.of(r1, r2));
        when(positionRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(position));
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(activity));
        when(volunteerCertificateRepository.findByVolunteerIdAndCertNameIn(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(cert(11L, "护理资格证", LocalDate.now().plusDays(5))));

        long occupied = rosterEligibilityService.countEligible(10L);
        assertEquals(0L, occupied);
    }

    @Test
    void grouped_counts_only_eligible_positions() {
        Position p10 = position;
        Position p20 = new Position();
        p20.setId(20L);
        p20.setActivityId(2L);
        p20.setRequiredCertificates(null); // 无证书要求
        Registration a = candidate(1L, 11L);
        Registration b = candidate(2L, 12L);
        b.setPositionId(20L);

        when(registrationRepository.findAllOccupyingCandidates()).thenReturn(List.of(a, b));
        when(positionRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(p10, p20));
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(activity));
        // 岗位 10 的人证件已过期；岗位 20 无证书要求
        when(volunteerCertificateRepository.findByVolunteerIdAndCertNameIn(org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(cert(11L, "护理资格证", LocalDate.now().minusDays(1))));

        Map<Long, Long> counts = rosterEligibilityService.countEligibleGroupByPosition();
        assertEquals(0L, counts.getOrDefault(10L, 0L));
        assertEquals(1L, counts.getOrDefault(20L, 0L));
    }

    @Test
    void evaluate_flags_expired_and_ended_activity() {
        Registration r = candidate(1L, 11L);
        when(positionRepository.findById(10L)).thenReturn(java.util.Optional.of(position));
        when(activityRepository.findById(2L)).thenReturn(java.util.Optional.of(activity));
        when(volunteerCertificateRepository.findByVolunteerId(11L))
                .thenReturn(List.of(cert(11L, "护理资格证", LocalDate.now().minusDays(1))));

        RosterEligibilityService.Eligibility eligibility = rosterEligibilityService.evaluate(r);
        assertFalse(eligibility.isEligible());
        assertTrue(eligibility.isActivityOngoing());
        assertTrue(eligibility.getExpiredCertificates().contains("护理资格证"));
    }
}
