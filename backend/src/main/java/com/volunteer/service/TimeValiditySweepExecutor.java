package com.volunteer.service;

import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Activity;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.repository.ActivityRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 时效扫描的事务执行体：每个扫描面一条事务，行锁顺序与「报名/通过/改门槛」一致 ——
 * 先活动/岗位行，再报名行。因此「改有效期和点通过叠在同一秒」时账上只有一种结局：
 * 过期则通过不成立，或通过当时证件仍在有效期内。
 */
@Service
public class TimeValiditySweepExecutor {

    private static final Logger log = LoggerFactory.getLogger(TimeValiditySweepExecutor.class);

    /** 需要随时效立即重检的状态：在途单 + 已批完/已通过的人 + 此前被卡的人 */
    private static final List<Integer> SWEEP_STATUSES =
            List.of(ApprovalStatus.PENDING.getCode(),
                    ApprovalStatus.APPROVED.getCode(),
                    ApprovalStatus.COMPLETED.getCode(),
                    ApprovalStatus.CHECK_FAILED.getCode());

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private CapabilityValidationService capabilityValidationService;

    @Autowired
    private RegistrationLiveCheckService registrationLiveCheckService;

    /**
     * 散场即清：先锁活动行（与报名/通过互斥），再锁该活动相关报名，
     * 活动时效失败立即腾位。
     */
    @Transactional
    public void sweepEndedActivities() {
        List<Activity> ended = activityRepository.findEndedActivities();
        for (Activity candidate : ended) {
            Activity activity = activityRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (activity == null || !capabilityValidationService.isActivityEnded(activity)) {
                continue;
            }
            List<Registration> registrations = registrationRepository
                    .findByActivityIdAndStatusInForUpdate(activity.getId(), SWEEP_STATUSES);
            for (Registration registration : registrations) {
                Position position = positionRepository.findById(registration.getPositionId()).orElse(null);
                CapabilityCheckResult result =
                        registrationLiveCheckService.settle(registration, position, activity);
                registrationRepository.save(registration);
                if (Boolean.FALSE.equals(result.getPass())) {
                    log.info("活动[{}]已散场，报名单[{}]清场：在途停校验/已批不计满员",
                            activity.getId(), registration.getId());
                }
            }
        }
    }

    /**
     * 证书有效期被改写后立即清场（不等下一轮 30s 扫描）：
     * 先锁该证书可能影响的全部要求同名证书的岗位，再锁该志愿者在这些岗位下的报名，
     * 锁顺序仍是岗位 → 报名，与门槛写入、通过动作一致。
     */
    @Transactional
    public void settleVolunteerCertificates(Long volunteerId) {
        List<Position> positions = positionRepository.findAll().stream()
                .filter(p -> p.getRequiredCertificates() != null && !p.getRequiredCertificates().isBlank())
                .toList();
        for (Position p : positions) {
            Position locked = positionRepository.findByIdForUpdate(p.getId()).orElse(null);
            if (locked == null) {
                continue;
            }
            List<Registration> registrations = registrationRepository
                    .findByVolunteerIdAndPositionIdAndStatusInForUpdate(
                            volunteerId, locked.getId(), SWEEP_STATUSES);
            for (Registration registration : registrations) {
                CapabilityCheckResult result = capabilityValidationService
                        .validateAgainstPosition(volunteerId, locked);
                if (!registrationLiveCheckService.needsSettle(registration,
                        Boolean.TRUE.equals(result.getPass()))) {
                    continue;
                }
                registrationLiveCheckService.settle(registration, locked, null);
                registrationRepository.save(registration);
            }
        }
    }

    /**
     * 活动被改写（如结束时间改到当前钟点之前、状态置为已结束）后立即清场。
     * 先锁活动行，再锁该活动报名，与散场扫描、报名、通过同一顺序。
     */
    @Transactional
    public void settleActivity(Long activityId) {
        Activity activity = activityRepository.findByIdForUpdate(activityId).orElse(null);
        if (activity == null || !capabilityValidationService.isActivityEnded(activity)) {
            return;
        }
        List<Registration> registrations = registrationRepository
                .findByActivityIdAndStatusInForUpdate(activityId, SWEEP_STATUSES);
        for (Registration registration : registrations) {
            Position position = positionRepository.findById(registration.getPositionId()).orElse(null);
            registrationLiveCheckService.settle(registration, position, activity);
            registrationRepository.save(registration);
        }
    }

    /**
     * 证书到期即清：对要求持证的岗位逐岗扫描，持锁现场复核在途/已批报名。
     * 证书续期后，下一轮扫描现场复核通过，在途单自动恢复被卡前节点重新占编。
     */
    @Transactional
    public void sweepExpiredCertificates() {
        List<Position> positions = positionRepository.findAll();
        for (Position p : positions) {
            if (p.getRequiredCertificates() == null || p.getRequiredCertificates().isBlank()) {
                continue;
            }
            List<Registration> registrations = registrationRepository
                    .findByPositionIdAndStatusInForUpdate(p.getId(), SWEEP_STATUSES);
            for (Registration registration : registrations) {
                CapabilityCheckResult result = capabilityValidationService
                        .validateAgainstPosition(registration.getVolunteerId(), p);
                if (!registrationLiveCheckService.needsSettle(registration,
                        Boolean.TRUE.equals(result.getPass()))) {
                    continue;
                }
                registrationLiveCheckService.settle(registration, p, null);
                registrationRepository.save(registration);
                if (Boolean.FALSE.equals(result.getPass())) {
                    log.info("报名单[{}]的必备证书已过有效期，在途停校验/已批不计满员",
                            registration.getId());
                }
            }
        }
    }
}
