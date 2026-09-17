package com.volunteer.service;

import com.volunteer.dto.request.RegistrationRequest;
import com.volunteer.dto.response.RegistrationDetail;
import com.volunteer.entity.Activity;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.entity.Volunteer;
import com.volunteer.entity.VolunteerCertificate;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.repository.ActivityRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import com.volunteer.repository.VolunteerCertificateRepository;
import com.volunteer.repository.VolunteerRepository;
import com.volunteer.config.TestMockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全链路验收（真实 ORM + H2 + 真实 Service 装配）：
 * 证书过期、活动散场两头都拦 —— 待审停校验/通过不成立/立即让出名额、
 * 已批完保留历史但不计满员、散场后新报名失败、续期后恢复。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
class TimeValidityEndToEndTest {

    @Autowired private ActivityRepository activityRepository;
    @Autowired private PositionRepository positionRepository;
    @Autowired private VolunteerRepository volunteerRepository;
    @Autowired private VolunteerCertificateRepository certificateRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private RegistrationService registrationService;
    @Autowired private PositionService positionService;
    @Autowired private TimeValiditySweepExecutor sweepExecutor;

    private Activity ongoingActivity;
    private Activity endedActivity;
    private Position certPosition;
    private Volunteer nurse;
    private VolunteerCertificate nursingCert;

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        certificateRepository.deleteAll();
        positionRepository.deleteAll();
        volunteerRepository.deleteAll();
        activityRepository.deleteAll();

        ongoingActivity = new Activity();
        ongoingActivity.setName("今晚的敬老院行动");
        ongoingActivity.setStartTime(LocalDateTime.now().minusHours(1));
        ongoingActivity.setEndTime(LocalDateTime.now().plusDays(1));
        ongoingActivity.setStatus(1);
        ongoingActivity = activityRepository.save(ongoingActivity);

        endedActivity = new Activity();
        endedActivity.setName("已散场的活动");
        endedActivity.setStartTime(LocalDateTime.now().minusDays(2));
        endedActivity.setEndTime(LocalDateTime.now().minusHours(2));
        endedActivity.setStatus(1);
        endedActivity = activityRepository.save(endedActivity);

        certPosition = new Position();
        certPosition.setActivityId(ongoingActivity.getId());
        certPosition.setName("生活照料员");
        certPosition.setRequiredCertificates("护理资格证");
        certPosition.setRequiredHours(0);
        certPosition.setMinCount(1);
        certPosition.setMaxCount(5);
        certPosition.setStatus(1);
        certPosition = positionRepository.save(certPosition);

        nurse = new Volunteer();
        nurse.setName("王护理");
        nurse.setTotalHours(java.math.BigDecimal.ZERO);
        nurse.setStatus(1);
        nurse = volunteerRepository.save(nurse);

        nursingCert = new VolunteerCertificate();
        nursingCert.setVolunteerId(nurse.getId());
        nursingCert.setCertName("护理资格证");
        nursingCert.setIssueDate(LocalDate.now().minusYears(2));
        nursingCert.setExpireDate(LocalDate.now().plusYears(1));
        nursingCert = certificateRepository.save(nursingCert);
    }

    private Registration register(Activity activity) {
        RegistrationRequest req = new RegistrationRequest();
        req.setVolunteerId(nurse.getId());
        req.setActivityId(activity.getId());
        req.setPositionId(certPosition.getId());
        req.setApplyMessage("申请");
        return registrationService.createRegistration(req);
    }

    @Test
    void expired_certificate_blocks_pending_unoccupies_and_renew_restores() {
        Registration reg = register(ongoingActivity);
        assertEquals(ApprovalStatus.PENDING.getCode(), reg.getStatus());
        assertEquals(1L, positionService.getOccupiedCount(certPosition.getId()));

        // 护理资格证昨天到期：扫描立即停校验、腾位
        nursingCert.setExpireDate(LocalDate.now().minusDays(1));
        certificateRepository.save(nursingCert);
        sweepExecutor.settleVolunteerCertificates(nurse.getId());

        Registration stuck = registrationRepository.findById(reg.getId()).orElseThrow();
        assertEquals(ApprovalStatus.CHECK_FAILED.getCode(), stuck.getStatus());
        assertEquals(0, stuck.getRecheckPass());
        assertEquals(ApprovalNode.LEADER.getLevel(), stuck.getResumeNode());
        assertEquals(0L, positionService.getOccupiedCount(certPosition.getId()));

        RegistrationDetail detail = registrationService.getRegistrationDetail(reg.getId());
        assertTrue(detail.getCertExpired());
        assertTrue(detail.getTimeBlocked());

        // 续期：在途单恢复被卡前节点重新占编
        nursingCert.setExpireDate(LocalDate.now().plusYears(1));
        certificateRepository.save(nursingCert);
        sweepExecutor.settleVolunteerCertificates(nurse.getId());

        Registration resumed = registrationRepository.findById(reg.getId()).orElseThrow();
        assertEquals(ApprovalStatus.PENDING.getCode(), resumed.getStatus());
        assertEquals(ApprovalNode.LEADER.getLevel(), resumed.getCurrentApprovalNode());
        assertEquals(1L, positionService.getOccupiedCount(certPosition.getId()));
    }

    @Test
    void expired_certificate_keeps_completed_history_but_removes_from_occupancy() {
        Registration reg = register(ongoingActivity);

        // 批完
        nursingCert.setExpireDate(LocalDate.now().plusYears(1));
        certificateRepository.save(nursingCert);
        sweepExecutor.settleVolunteerCertificates(nurse.getId());
        Registration completed = registrationRepository.findById(reg.getId()).orElseThrow();
        completed.setStatus(ApprovalStatus.COMPLETED.getCode());
        completed.setCurrentApprovalNode(ApprovalNode.COMPLETED.getLevel());
        registrationRepository.save(completed);
        assertEquals(1L, positionService.getOccupiedCount(certPosition.getId()));

        // 证书昨天到期：历史记录保留，但今晚排班人数不再数这个人
        nursingCert.setExpireDate(LocalDate.now().minusDays(1));
        certificateRepository.save(nursingCert);
        sweepExecutor.settleVolunteerCertificates(nurse.getId());

        Registration stillCompleted = registrationRepository.findById(reg.getId()).orElseThrow();
        assertEquals(ApprovalStatus.COMPLETED.getCode(), stillCompleted.getStatus());
        assertEquals(ApprovalNode.COMPLETED.getLevel(), stillCompleted.getCurrentApprovalNode());
        assertEquals(0, stillCompleted.getRecheckPass());
        assertEquals(0L, positionService.getOccupiedCount(certPosition.getId()));
    }

    @Test
    void ended_activity_rejects_new_registration_and_fails_pending_pass() {
        // 散场后新报名失败，不产生报名单
        RegistrationRequest req = new RegistrationRequest();
        req.setVolunteerId(nurse.getId());
        req.setActivityId(endedActivity.getId());
        req.setPositionId(certPosition.getId());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registrationService.createRegistration(req));
        assertTrue(ex.getMessage().contains("活动已结束"));
        assertEquals(0, registrationRepository.count());

        // 已在途的单，活动一散场即停校验腾位
        Registration inflight = register(ongoingActivity);
        ongoingActivity.setEndTime(LocalDateTime.now().minusMinutes(1));
        activityRepository.save(ongoingActivity);
        sweepExecutor.settleActivity(ongoingActivity.getId());

        Registration blocked = registrationRepository.findById(inflight.getId()).orElseThrow();
        assertEquals(ApprovalStatus.CHECK_FAILED.getCode(), blocked.getStatus());
        assertEquals(0L, positionService.getOccupiedCount(certPosition.getId()));
        RegistrationDetail detail = registrationService.getRegistrationDetail(inflight.getId());
        assertTrue(detail.getActivityEnded());
        assertNotNull(detail.getBlockReason());
    }

    @Test
    void healthy_registration_is_not_touched_by_sweep() {
        Registration reg = register(ongoingActivity);
        sweepExecutor.sweepExpiredCertificates();
        sweepExecutor.sweepEndedActivities();

        Registration fresh = registrationRepository.findById(reg.getId()).orElseThrow();
        assertEquals(ApprovalStatus.PENDING.getCode(), fresh.getStatus());
        assertNull(fresh.getRecheckPass());
        assertFalse(registrationService.getRegistrationDetail(reg.getId()).getTimeBlocked());
    }
}
