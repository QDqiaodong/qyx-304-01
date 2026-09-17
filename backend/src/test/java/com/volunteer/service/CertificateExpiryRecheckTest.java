package com.volunteer.service;

import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Activity;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.entity.VolunteerCertificate;
import com.volunteer.enums.ApprovalNode;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 证件时效闸门：改有效期与在途报名重检同事务。
 * 改后已过期：待审停住、通过不成立、让出名额，已批完保留记录但不占编；
 * 续期：被证件闸门卡住的单回到被卡前节点。
 */
@ExtendWith(MockitoExtension.class)
class CertificateExpiryRecheckTest {

    @Mock private VolunteerCertificateRepository certificateRepository;
    @Mock private RegistrationRepository registrationRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private CapabilityValidationService capabilityValidationService;
    @Spy private RegistrationRecheckSupport recheckSupport = new RegistrationRecheckSupport();

    @InjectMocks
    private CertificateService certificateService;

    private VolunteerCertificate certificate;
    private Position position;
    private Activity activity;

    @BeforeEach
    void setUp() {
        certificate = new VolunteerCertificate();
        certificate.setId(7L);
        certificate.setVolunteerId(5L);
        certificate.setCertName("护理资格证");
        certificate.setExpireDate(LocalDate.now().plusDays(10));

        position = new Position();
        position.setId(10L);
        position.setActivityId(2L);
        position.setRequiredCertificates("护理资格证");

        activity = new Activity();
        activity.setId(2L);
        activity.setStatus(1);
        activity.setEndTime(LocalDateTime.now().plusDays(5));
    }

    private Registration inflight() {
        Registration r = new Registration();
        r.setId(100L);
        r.setVolunteerId(5L);
        r.setActivityId(2L);
        r.setPositionId(10L);
        r.setStatus(ApprovalStatus.PENDING.getCode());
        r.setCurrentApprovalNode(ApprovalNode.MANAGER.getLevel());
        r.setCheckPass(1);
        return r;
    }

    private Registration completed() {
        Registration r = inflight();
        r.setId(101L);
        r.setStatus(ApprovalStatus.COMPLETED.getCode());
        r.setCurrentApprovalNode(ApprovalNode.COMPLETED.getLevel());
        return r;
    }

    private CapabilityCheckResult failExpired() {
        CapabilityCheckResult result = new CapabilityCheckResult();
        result.setPass(false);
        result.setExpiredCertificates(List.of("护理资格证"));
        return result;
    }

    private CapabilityCheckResult pass() {
        CapabilityCheckResult result = new CapabilityCheckResult();
        result.setPass(true);
        return result;
    }

    @Test
    void changing_expiry_to_past_blocks_inflight_and_keeps_completed_record() {
        Registration inflight = inflight();
        Registration completed = completed();
        when(certificateRepository.findById(7L)).thenReturn(Optional.of(certificate));
        when(registrationRepository.findByVolunteerIdAndStatusInForUpdate(eq(5L), any()))
                .thenReturn(List.of(inflight, completed));
        when(certificateRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(certificate));
        when(certificateRepository.save(any(VolunteerCertificate.class))).thenAnswer(i -> i.getArgument(0));
        when(positionRepository.findById(10L)).thenReturn(Optional.of(position));
        when(activityRepository.findById(2L)).thenReturn(Optional.of(activity));
        when(capabilityValidationService.validateAgainstPosition(eq(5L), eq(position), any(LocalDate.class), eq(true)))
                .thenReturn(failExpired());

        VolunteerCertificate input = new VolunteerCertificate();
        input.setCertName("护理资格证");
        input.setExpireDate(LocalDate.now().minusDays(1));
        certificateService.updateCertificate(7L, input);

        // 重检结论已过期
        assertEquals(LocalDate.now().minusDays(1), certificate.getExpireDate());

        // 在途单：停在能力校验失败、记住负责人节点、让出名额
        assertEquals(ApprovalStatus.CHECK_FAILED.getCode(), inflight.getStatus());
        assertEquals("CERT", inflight.getBlockReason());
        assertEquals(ApprovalNode.MANAGER.getLevel(), inflight.getResumeNode());
        assertEquals(0, inflight.getRecheckPass());

        // 已批完：历史结果保留，但让出满员名额
        assertEquals(ApprovalStatus.COMPLETED.getCode(), completed.getStatus());
        assertEquals(0, completed.getRecheckPass());
    }

    @Test
    void renewing_expired_certificate_resumes_blocked_inflight() {
        certificate.setExpireDate(LocalDate.now().minusDays(2));

        Registration blocked = inflight();
        blocked.setStatus(ApprovalStatus.CHECK_FAILED.getCode());
        blocked.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
        blocked.setResumeNode(ApprovalNode.MANAGER.getLevel());
        blocked.setBlockReason("CERT");
        blocked.setRecheckPass(0);

        when(certificateRepository.findById(7L)).thenReturn(Optional.of(certificate));
        when(registrationRepository.findByVolunteerIdAndStatusInForUpdate(eq(5L), any()))
                .thenReturn(List.of(blocked));
        when(certificateRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(certificate));
        when(certificateRepository.save(any(VolunteerCertificate.class))).thenAnswer(i -> i.getArgument(0));
        when(positionRepository.findById(10L)).thenReturn(Optional.of(position));
        when(activityRepository.findById(2L)).thenReturn(Optional.of(activity));
        when(capabilityValidationService.validateAgainstPosition(eq(5L), eq(position), any(LocalDate.class), eq(true)))
                .thenReturn(pass());

        VolunteerCertificate input = new VolunteerCertificate();
        input.setCertName("护理资格证");
        input.setExpireDate(LocalDate.now().plusYears(1));
        certificateService.updateCertificate(7L, input);

        // 续期后回到被卡前的负责人节点，重新占编
        assertEquals(ApprovalStatus.PENDING.getCode(), blocked.getStatus());
        assertEquals(ApprovalNode.MANAGER.getLevel(), blocked.getCurrentApprovalNode());
        assertEquals(1, blocked.getRecheckPass());
        assertNotNull(blocked.getRecheckResult());
    }

    @Test
    void unrelated_position_registrations_are_not_touched() {
        Position other = new Position();
        other.setId(99L);
        other.setActivityId(2L);
        other.setRequiredCertificates("其它证");

        Registration r = inflight();
        r.setPositionId(99L);

        when(certificateRepository.findById(7L)).thenReturn(Optional.of(certificate));
        when(registrationRepository.findByVolunteerIdAndStatusInForUpdate(eq(5L), any()))
                .thenReturn(List.of(r));
        when(certificateRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(certificate));
        when(certificateRepository.save(any(VolunteerCertificate.class))).thenAnswer(i -> i.getArgument(0));
        when(positionRepository.findById(99L)).thenReturn(Optional.of(other));

        VolunteerCertificate input = new VolunteerCertificate();
        input.setCertName("护理资格证");
        input.setExpireDate(LocalDate.now().minusDays(1));
        certificateService.updateCertificate(7L, input);

        verify(capabilityValidationService, never())
                .validateAgainstPosition(anyLong(), any(Position.class), any(LocalDate.class),
                        org.mockito.ArgumentMatchers.anyBoolean());
        verify(registrationRepository, never()).save(any(Registration.class));
    }
}
