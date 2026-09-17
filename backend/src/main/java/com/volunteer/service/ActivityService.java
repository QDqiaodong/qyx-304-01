package com.volunteer.service;

import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Activity;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.enums.BlockReason;
import com.volunteer.repository.ActivityRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import com.volunteer.util.GateRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 活动写入与「活动是否在办」闸门联动。
 *
 * 活动散场（状态置结束 / 结束钟点被改到当前之前）后：
 *   - 新报名直接失败（见 RegistrationService）；
 *   - 还没批完的通过直接失败，在途单停在能力校验失败并让出名额；
 *   - 已批完的保留历史审批记录，但今晚排班不再计数。
 * 活动重新开启或延期后，被「活动散场」闸门卡住的在途单回到被卡前节点。
 *
 * 加锁顺序与通过动作一致：先活动行（FOR UPDATE），再按 id 升序锁报名行，
 * 需要岗位快照时只做普通读，杜绝互锁。
 */
@Service
public class ActivityService {

    private static final List<Integer> RECHECK_STATUSES =
            List.of(ApprovalStatus.PENDING.getCode(),
                    ApprovalStatus.APPROVED.getCode(),
                    ApprovalStatus.COMPLETED.getCode(),
                    ApprovalStatus.CHECK_FAILED.getCode());

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private CapabilityValidationService capabilityValidationService;

    @Autowired
    private RegistrationRecheckSupport recheckSupport;

    @Transactional
    public Activity createActivity(Activity activity) {
        return activityRepository.save(activity);
    }

    @Transactional
    public Activity updateActivity(Long id, Activity input) {
        Activity existing = activityRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));

        existing.setName(input.getName());
        existing.setDescription(input.getDescription());
        existing.setStartTime(input.getStartTime());
        existing.setEndTime(input.getEndTime());
        existing.setLocation(input.getLocation());
        existing.setStatus(input.getStatus());

        Activity saved = activityRepository.save(existing);
        recheckActivityRegistrations(saved);
        return saved;
    }

    /**
     * 活动行锁持有中：按 id 升序锁该活动全部在途/已批报名并逐单过账。
     */
    private void recheckActivityRegistrations(Activity activity) {
        List<Registration> registrations = registrationRepository
                .findByActivityIdAndStatusInForUpdate(activity.getId(), RECHECK_STATUSES);
        boolean ongoing = GateRules.isOngoing(activity);
        LocalDate today = LocalDate.now();

        for (Registration registration : registrations) {
            Position position = positionRepository.findById(registration.getPositionId()).orElse(null);
            CapabilityCheckResult result = position == null
                    ? failResult("岗位不存在")
                    : capabilityValidationService.validateAgainstPosition(
                            registration.getVolunteerId(), position, today, true);

            if (ApprovalStatus.CHECK_FAILED.getCode().equals(registration.getStatus())
                    && Boolean.TRUE.equals(result.getPass()) && ongoing) {
                recheckSupport.resumeIfCleared(registration, result, ongoing, BlockReason.ACTIVITY);
            } else {
                recheckSupport.applyLiveOutcome(registration, result, ongoing);
            }
            registrationRepository.save(registration);
        }
    }

    private CapabilityCheckResult failResult(String message) {
        CapabilityCheckResult result = new CapabilityCheckResult();
        result.setPass(false);
        result.setSkillCheck(List.of());
        result.setCertCheck(List.of());
        result.setExpiredCertificates(List.of());
        result.setHoursCheck("服务时长 - 无要求");
        result.setMessage(message);
        return result;
    }
}
