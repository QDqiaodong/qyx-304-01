package com.volunteer.service;

import com.volunteer.entity.Activity;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.entity.VolunteerCertificate;
import com.volunteer.repository.ActivityRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import com.volunteer.repository.VolunteerCertificateRepository;
import com.volunteer.util.GateRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排班口今晚到岗的实时资格口径。
 *
 * 门槛口径的占编候选集（状态占编 + 有效校验通过）之上，逐单再过两道硬闸门：
 *   1. 活动此刻仍在办（状态进行中且未过结束钟点）；
 *   2. 岗位所需证书此刻仍在有效期内。
 *
 * 任何一道不满足都立即腾位：不依赖改证件、改活动触发重检，
 * 证件自然到期、活动自然散场的当晚就不再计入满员人数（历史审批记录保留）。
 */
@Service
public class RosterEligibilityService {

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private VolunteerCertificateRepository volunteerCertificateRepository;

    /** 单个岗位今晚实际占编人数 */
    public long countEligible(Long positionId) {
        return countEligible(registrationRepository.findOccupyingCandidatesByPositionId(positionId),
                LocalDate.now());
    }

    /** 全部岗位今晚实际占编人数：positionId -> 人数 */
    public Map<Long, Long> countEligibleGroupByPosition() {
        return countEligibleGrouped(registrationRepository.findAllOccupyingCandidates(), LocalDate.now());
    }

    /**
     * 单个报名此刻是否仍具备到岗资格（前端列表涂过期色、通过按钮启停也走这里的数据）。
     * 返回 null 表示报名/岗位/活动查不到。
     */
    public Eligibility evaluate(Registration registration) {
        if (registration == null) {
            return null;
        }
        Position position = positionRepository.findById(registration.getPositionId()).orElse(null);
        Activity activity = activityRepository.findById(registration.getActivityId()).orElse(null);
        List<String> expired = expiredRequiredCertificates(registration.getVolunteerId(), position, LocalDate.now());
        boolean ongoing = GateRules.isOngoing(activity);
        return new Eligibility(ongoing && expired.isEmpty(), ongoing, expired);
    }

    private long countEligible(List<Registration> candidates, LocalDate today) {
        if (candidates.isEmpty()) {
            return 0;
        }
        Map<Long, Position> positions = loadPositions(candidates);
        Map<Long, Activity> activities = loadActivities(candidates);
        Map<Long, List<VolunteerCertificate>> certsByVolunteer = loadCertificates(candidates, positions);

        return candidates.stream()
                .filter(r -> isEligible(r,
                        positions.get(r.getPositionId()),
                        activities.get(r.getActivityId()),
                        certsByVolunteer.getOrDefault(r.getVolunteerId(), Collections.emptyList()),
                        today))
                .count();
    }

    private Map<Long, Long> countEligibleGrouped(List<Registration> candidates, LocalDate today) {
        Map<Long, Long> counts = new HashMap<>();
        if (candidates.isEmpty()) {
            return counts;
        }
        Map<Long, Position> positions = loadPositions(candidates);
        Map<Long, Activity> activities = loadActivities(candidates);
        Map<Long, List<VolunteerCertificate>> certsByVolunteer = loadCertificates(candidates, positions);

        for (Registration r : candidates) {
            if (isEligible(r,
                    positions.get(r.getPositionId()),
                    activities.get(r.getActivityId()),
                    certsByVolunteer.getOrDefault(r.getVolunteerId(), Collections.emptyList()),
                    today)) {
                counts.merge(r.getPositionId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private boolean isEligible(Registration r, Position position, Activity activity,
                               List<VolunteerCertificate> held, LocalDate today) {
        if (position == null || !GateRules.isOngoing(activity)) {
            return false;
        }
        return expiredRequired(position, held, today).isEmpty();
    }

    private List<String> expiredRequiredCertificates(Long volunteerId, Position position, LocalDate today) {
        if (position == null || !StringUtils.hasText(position.getRequiredCertificates())) {
            return Collections.emptyList();
        }
        return expiredRequired(position,
                volunteerCertificateRepository.findByVolunteerId(volunteerId), today);
    }

    /** 岗位所需证书中「未持有」或「已过期」的名称（未持有归门槛口径，这里只看时效，缺证不会进候选集） */
    private List<String> expiredRequired(Position position, List<VolunteerCertificate> held, LocalDate today) {
        if (!StringUtils.hasText(position.getRequiredCertificates())) {
            return Collections.emptyList();
        }
        Map<String, VolunteerCertificate> heldMap = new HashMap<>();
        for (VolunteerCertificate c : held) {
            heldMap.putIfAbsent(c.getCertName(), c);
        }
        List<String> expired = new ArrayList<>();
        for (String raw : position.getRequiredCertificates().split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            VolunteerCertificate c = heldMap.get(name);
            if (c != null && !GateRules.isCertificateValid(c, today)) {
                expired.add(name);
            }
        }
        return expired;
    }

    private Map<Long, Position> loadPositions(List<Registration> candidates) {
        Map<Long, Position> map = new HashMap<>();
        for (Position p : positionRepository.findAllById(
                candidates.stream().map(Registration::getPositionId).distinct().toList())) {
            map.put(p.getId(), p);
        }
        return map;
    }

    private Map<Long, Activity> loadActivities(List<Registration> candidates) {
        Map<Long, Activity> map = new HashMap<>();
        for (Activity a : activityRepository.findAllById(
                candidates.stream().map(Registration::getActivityId).distinct().toList())) {
            map.put(a.getId(), a);
        }
        return map;
    }

    /** 只加载候选岗位实际要求的证书，避免全表搬运 */
    private Map<Long, List<VolunteerCertificate>> loadCertificates(List<Registration> candidates,
                                                                    Map<Long, Position> positions) {
        Set<Long> volunteerIds = new HashSet<>();
        Set<String> requiredNames = new HashSet<>();
        for (Registration r : candidates) {
            Position p = positions.get(r.getPositionId());
            if (p != null && StringUtils.hasText(p.getRequiredCertificates())) {
                volunteerIds.add(r.getVolunteerId());
                for (String raw : p.getRequiredCertificates().split(",")) {
                    String name = raw.trim();
                    if (!name.isEmpty()) {
                        requiredNames.add(name);
                    }
                }
            }
        }
        Map<Long, List<VolunteerCertificate>> map = new HashMap<>();
        if (volunteerIds.isEmpty()) {
            return map;
        }
        for (Long volunteerId : volunteerIds) {
            map.put(volunteerId, volunteerCertificateRepository
                    .findByVolunteerIdAndCertNameIn(volunteerId, new ArrayList<>(requiredNames)));
        }
        return map;
    }

    /** 单条报名实时资格结论 */
    public static class Eligibility {
        /** 两道闸门都过，今晚可到岗 */
        private final boolean eligible;
        /** 活动是否仍在办 */
        private final boolean activityOngoing;
        /** 已过期的所需证书名称 */
        private final List<String> expiredCertificates;

        public Eligibility(boolean eligible, boolean activityOngoing, List<String> expiredCertificates) {
            this.eligible = eligible;
            this.activityOngoing = activityOngoing;
            this.expiredCertificates = expiredCertificates;
        }

        public boolean isEligible() {
            return eligible;
        }

        public boolean isActivityOngoing() {
            return activityOngoing;
        }

        public List<String> getExpiredCertificates() {
            return expiredCertificates;
        }
    }
}
