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

import java.util.List;
import java.util.Map;

/**
 * 岗位门槛写入与岗位占编口径。
 *
 * 门槛（技能/证书/服务时长）加严或改写，与在途报名的重检绑在同一条事务里：
 * 先锁岗位行（version+1），再锁该岗位全部在途/已批报名并逐单按新门槛复核。
 * 与「通过」动作约定同一加锁顺序（活动 → 岗位 → 报名），二者并发时账上只许出现一种结局。
 */
@Service
public class PositionService {

    /** 占编状态：待审批、已通过、审批完成。退回未重提、驳回、能力校验失败均不占编 */
    public static final List<Integer> OCCUPYING_STATUSES =
            List.of(ApprovalStatus.PENDING.getCode(),
                    ApprovalStatus.APPROVED.getCode(),
                    ApprovalStatus.COMPLETED.getCode());

    /** 需要随门槛改动立即重检的状态：在途单 + 已批完的人 + 此前被闸门卡住的单 */
    private static final List<Integer> RECHECK_STATUSES =
            List.of(ApprovalStatus.PENDING.getCode(),
                    ApprovalStatus.APPROVED.getCode(),
                    ApprovalStatus.COMPLETED.getCode(),
                    ApprovalStatus.CHECK_FAILED.getCode());

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private CapabilityValidationService capabilityValidationService;

    @Autowired
    private PositionCacheService positionCacheService;

    @Autowired
    private RosterEligibilityService rosterEligibilityService;

    @Autowired
    private RegistrationRecheckSupport recheckSupport;

    /**
     * 更新岗位。门槛字段（技能/证书/服务时长）任一发生变化即 bump version，
     * 并在同一事务内对全部在途及已批报名按新门槛重检：
     * - 在途单复核失败：停在能力校验失败、让出名额、通过动作直接不成立，记住原节点；
     * - 已批完的人不做清退，但复核失败即不占满员名额；
     * - 此前被卡的单子在放宽后复核通过：在途单恢复到被卡前节点，重新占编。
     */
    @Transactional
    public Position updatePosition(Long id, Position input) {
        Position existing = positionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("岗位不存在"));

        boolean thresholdChanged = thresholdChanged(existing, input);

        existing.setName(input.getName());
        existing.setRequiredSkills(trimToNull(input.getRequiredSkills()));
        existing.setRequiredCertificates(trimToNull(input.getRequiredCertificates()));
        existing.setRequiredHours(input.getRequiredHours() == null ? 0 : input.getRequiredHours());
        existing.setMinCount(input.getMinCount());
        existing.setMaxCount(input.getMaxCount());
        existing.setStatus(input.getStatus());

        if (thresholdChanged) {
            int newVersion = (existing.getRequirementVersion() == null ? 1 : existing.getRequirementVersion()) + 1;
            existing.setRequirementVersion(newVersion);
        }

        Position saved = positionRepository.save(existing);

        if (thresholdChanged) {
            recheckInTransaction(saved);
        }

        positionCacheService.clearCache(id);
        positionCacheService.cachePositionCapability(id);

        return attachOccupancy(saved);
    }

    @Transactional
    public Position createPosition(Position position) {
        Position saved = positionRepository.save(position);
        positionCacheService.clearCache(saved.getId());
        positionCacheService.cachePositionCapability(saved.getId());
        return attachOccupancy(saved);
    }

    /**
     * 门槛已在本事务内落库（岗位行锁持有中），对在途/已批报名逐单复核。
     * 证件时效按「今天」现场核，活动是否在办也现场判，三道闸门一次过账。
     */
    private void recheckInTransaction(Position position) {
        List<Registration> registrations = registrationRepository.findByPositionIdAndStatusInForUpdate(
                position.getId(), RECHECK_STATUSES);

        Activity activity = activityRepository.findById(position.getActivityId()).orElse(null);
        boolean ongoing = GateRules.isOngoing(activity);

        for (Registration registration : registrations) {
            // 报名行锁持有中，证件以 FOR UPDATE 读出（报名 → 证件同序），
            // 与证件有效期写入并发时拿到的一定是最新有效期
            CapabilityCheckResult result = capabilityValidationService.validateAgainstPosition(
                    registration.getVolunteerId(), position, java.time.LocalDate.now(), true);

            if (ApprovalStatus.CHECK_FAILED.getCode().equals(registration.getStatus())
                    && Boolean.TRUE.equals(result.getPass()) && ongoing) {
                // 此前被卡（门槛/证件/活动）的单在门槛放宽后尝试恢复，
                // 只有被卡原因正是门槛且另两道闸门仍过才放行
                recheckSupport.resumeIfCleared(registration, result, ongoing, BlockReason.THRESHOLD);
            } else {
                // 活动散场或复核不过：统一落账（已批完仅让出名额，在途单停住）
                recheckSupport.applyLiveOutcome(registration, result, ongoing);
            }
            registrationRepository.save(registration);
        }
    }

    private boolean thresholdChanged(Position oldPos, Position newPos) {
        String oldSkills = trimToNull(oldPos.getRequiredSkills());
        String newSkills = trimToNull(newPos.getRequiredSkills());
        String oldCerts = trimToNull(oldPos.getRequiredCertificates());
        String newCerts = trimToNull(newPos.getRequiredCertificates());
        int oldHours = oldPos.getRequiredHours() == null ? 0 : oldPos.getRequiredHours();
        int newHours = newPos.getRequiredHours() == null ? 0 : newPos.getRequiredHours();

        return !java.util.Objects.equals(oldSkills, newSkills)
                || !java.util.Objects.equals(oldCerts, newCerts)
                || oldHours != newHours;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ---------- 占编人数（排班口实时满员口径唯一入口） ----------

    public long getOccupiedCount(Long positionId) {
        return rosterEligibilityService.countEligible(positionId);
    }

    public Position attachOccupancy(Position position) {
        if (position == null) {
            return null;
        }
        long occupied = rosterEligibilityService.countEligible(position.getId());
        position.setOccupiedCount((int) occupied);
        int max = position.getMaxCount() == null ? 0 : position.getMaxCount();
        position.setFull(occupied >= max && max > 0);
        return position;
    }

    public List<Position> attachOccupancy(List<Position> positions) {
        if (positions == null || positions.isEmpty()) {
            return positions;
        }
        Map<Long, Long> counts = rosterEligibilityService.countEligibleGroupByPosition();
        for (Position position : positions) {
            long occupied = counts.getOrDefault(position.getId(), 0L);
            position.setOccupiedCount((int) occupied);
            int max = position.getMaxCount() == null ? 0 : position.getMaxCount();
            position.setFull(occupied >= max && max > 0);
        }
        return positions;
    }
}
