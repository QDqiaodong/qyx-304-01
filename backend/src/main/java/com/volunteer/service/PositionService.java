package com.volunteer.service;

import com.alibaba.fastjson.JSON;
import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位门槛写入与岗位占编口径。
 *
 * 门槛（技能/证书/服务时长）加严或改写，与在途报名的重检绑在同一条事务里：
 * 先锁岗位行（version+1），再锁该岗位全部在途/已批报名并逐单按新门槛复核。
 * 与「通过」动作约定同一加锁顺序（岗位 → 报名），二者并发时账上只许出现一种结局。
 */
@Service
public class PositionService {

    /** 占编状态：待审批、已通过、审批完成。退回未重提、驳回、能力校验失败均不占编 */
    public static final List<Integer> OCCUPYING_STATUSES =
            List.of(ApprovalStatus.PENDING.getCode(),
                    ApprovalStatus.APPROVED.getCode(),
                    ApprovalStatus.COMPLETED.getCode());

    /** 需要随门槛改动立即重检的状态：在途单 + 已批完的人 */
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
    private CapabilityValidationService capabilityValidationService;

    @Autowired
    private PositionCacheService positionCacheService;

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
     * 入参 position 为行锁读出的最新岗位，志愿者技能/证书/时长也在同一快照读取。
     */
    private void recheckInTransaction(Position position) {
        List<Registration> registrations = registrationRepository.findByPositionIdAndStatusInForUpdate(
                position.getId(), RECHECK_STATUSES);

        for (Registration registration : registrations) {
            CapabilityCheckResult result = capabilityValidationService.validateAgainstPosition(
                    registration.getVolunteerId(), position);

            registration.setRecheckResult(JSON.toJSONString(result));
            registration.setRecheckPass(result.getPass() ? 1 : 0);

            Integer status = registration.getStatus();
            if (result.getPass()) {
                // 此前因收紧被卡在能力校验失败的在途单，放宽后恢复到被卡前节点重新占编
                if (ApprovalStatus.CHECK_FAILED.getCode().equals(status)
                        && registration.getResumeNode() != null) {
                    registration.setStatus(ApprovalStatus.PENDING.getCode());
                    registration.setCurrentApprovalNode(registration.getResumeNode());
                    registration.setResumeNode(null);
                }
                // 已批完的人复核通过：审批结果不动，继续占编
                registrationRepository.save(registration);
            } else {
                if (ApprovalStatus.COMPLETED.getCode().equals(status)
                        || ApprovalStatus.APPROVED.getCode().equals(status)) {
                    // 已经批完的人不必清退：保留审批结果与节点，仅靠 recheckPass=0 让出满员名额；
                    // save 必须执行，否则复核失败结论不落库、名额仍被占着
                    registrationRepository.save(registration);
                    continue;
                }
                // 在途单（含此前被卡的）：停在能力校验失败，通过动作直接不成立
                if (!ApprovalStatus.CHECK_FAILED.getCode().equals(status)) {
                    registration.setResumeNode(registration.getCurrentApprovalNode());
                }
                registration.setStatus(ApprovalStatus.CHECK_FAILED.getCode());
                registration.setCurrentApprovalNode(ApprovalNode.CAPABILITY_CHECK.getLevel());
                registrationRepository.save(registration);
            }
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

    // ---------- 占编人数（满员口径唯一入口） ----------

    public long getOccupiedCount(Long positionId) {
        return registrationRepository.countOccupiedByPositionId(positionId);
    }

    public Position attachOccupancy(Position position) {
        if (position == null) {
            return null;
        }
        long occupied = registrationRepository.countOccupiedByPositionId(position.getId());
        position.setOccupiedCount((int) occupied);
        int max = position.getMaxCount() == null ? 0 : position.getMaxCount();
        position.setFull(occupied >= max && max > 0);
        return position;
    }

    public List<Position> attachOccupancy(List<Position> positions) {
        if (positions == null || positions.isEmpty()) {
            return positions;
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : registrationRepository.countOccupiedGroupByPosition()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        for (Position position : positions) {
            long occupied = counts.getOrDefault(position.getId(), 0L);
            position.setOccupiedCount((int) occupied);
            int max = position.getMaxCount() == null ? 0 : position.getMaxCount();
            position.setFull(occupied >= max && max > 0);
        }
        return positions;
    }
}
