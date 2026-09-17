package com.volunteer.service;

import com.alibaba.fastjson.JSON;
import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Activity;
import com.volunteer.entity.Position;
import com.volunteer.entity.Volunteer;
import com.volunteer.entity.VolunteerCertificate;
import com.volunteer.entity.VolunteerSkill;
import com.volunteer.repository.ActivityRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.VolunteerCertificateRepository;
import com.volunteer.repository.VolunteerRepository;
import com.volunteer.repository.VolunteerSkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CapabilityValidationService {

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private VolunteerRepository volunteerRepository;

    @Autowired
    private VolunteerSkillRepository volunteerSkillRepository;

    @Autowired
    private VolunteerCertificateRepository volunteerCertificateRepository;

    @Autowired
    private ActivityRepository activityRepository;

    public CapabilityCheckResult validate(Long volunteerId, Long positionId) {
        Position position = positionRepository.findById(positionId).orElse(null);
        if (position == null) {
            return emptyFailResult("岗位不存在");
        }
        Activity activity = activityRepository.findById(position.getActivityId()).orElse(null);
        return validateAgainstContext(volunteerId, position, activity);
    }

    private CapabilityCheckResult emptyFailResult(String message) {
        CapabilityCheckResult result = new CapabilityCheckResult();
        result.setPass(false);
        result.setSkillCheck(new ArrayList<>());
        result.setCertCheck(new ArrayList<>());
        result.setHoursCheck("服务时长 - 无要求");
        result.setActivityCheck("活动时效 - 无活动");
        result.setActivityEnded(false);
        result.setCertExpired(false);
        result.setMessage(message);
        return result;
    }

    /**
     * 按调用方给定的岗位（通常是事务内行锁读出的最新门槛）校验，
     * 保证「通过」动作看到的门槛与门槛写入事务串行后唯一确定。
     *
     * 证书时效与活动时效随当前数据现场判定（非报名时快照）：
     * 护理资格证昨天到期 → 必备证书校验失败；活动结束钟点已过 → 活动时效校验失败。
     */
    public CapabilityCheckResult validateAgainstPosition(Long volunteerId, Position position) {
        Activity activity = position == null ? null
                : activityRepository.findById(position.getActivityId()).orElse(null);
        return validateAgainstContext(volunteerId, position, activity);
    }

    /** 调用方已在事务内行锁持有活动行（如「通过」动作）时，直接给最新活动现场。 */
    public CapabilityCheckResult validateAgainstContext(Long volunteerId, Position position, Activity activity) {
        CapabilityCheckResult result = new CapabilityCheckResult();
        List<String> skillCheck = new ArrayList<>();
        List<String> certCheck = new ArrayList<>();

        Volunteer volunteer = volunteerRepository.findById(volunteerId).orElse(null);

        if (position == null) {
            return emptyFailResult("岗位不存在");
        }

        if (volunteer == null) {
            return emptyFailResult("志愿者不存在");
        }

        boolean allPass = true;

        // ---- 活动时效：散场后新报名与通过都不成立 ----
        boolean activityEnded = activity != null && isActivityEnded(activity);
        String activityCheck;
        if (activity == null) {
            activityCheck = "活动时效 - 活动不存在";
            allPass = false;
        } else if (activityEnded) {
            activityCheck = "活动时效 [已于 " + activity.getEndTime() + " 结束] - 已散场";
            allPass = false;
        } else {
            activityCheck = "活动时效 [" + activity.getEndTime() + " 前有效] - 通过";
        }
        result.setActivityCheck(activityCheck);
        result.setActivityEnded(activityEnded);

        if (StringUtils.hasText(position.getRequiredSkills())) {
            String[] requiredSkills = position.getRequiredSkills().split(",");
            List<VolunteerSkill> volunteerSkills = volunteerSkillRepository.findByVolunteerId(volunteerId);
            Set<String> skillSet = volunteerSkills.stream()
                    .map(VolunteerSkill::getSkillName)
                    .collect(Collectors.toSet());

            for (String skill : requiredSkills) {
                skill = skill.trim();
                if (skillSet.contains(skill)) {
                    skillCheck.add("技能 [" + skill + "] - 通过");
                } else {
                    skillCheck.add("技能 [" + skill + "] - 未满足");
                    allPass = false;
                }
            }
        }

        boolean anyCertExpired = false;
        if (StringUtils.hasText(position.getRequiredCertificates())) {
            String[] requiredCerts = position.getRequiredCertificates().split(",");
            List<VolunteerCertificate> volunteerCerts = volunteerCertificateRepository.findByVolunteerId(volunteerId);
            Map<String, List<VolunteerCertificate>> certMap = volunteerCerts.stream()
                    .collect(Collectors.groupingBy(VolunteerCertificate::getCertName));

            LocalDate today = LocalDate.now();
            for (String cert : requiredCerts) {
                cert = cert.trim();
                List<VolunteerCertificate> held = certMap.get(cert);
                if (held == null || held.isEmpty()) {
                    certCheck.add("证书 [" + cert + "] - 未满足");
                    allPass = false;
                    continue;
                }
                // 同名证书取一张仍在有效期内的；expireDate 为空视为长期有效
                Optional<VolunteerCertificate> valid = held.stream()
                        .filter(c -> c.getExpireDate() == null || !c.getExpireDate().isBefore(today))
                        .findFirst();
                if (valid.isPresent()) {
                    VolunteerCertificate c = valid.get();
                    certCheck.add("证书 [" + cert + "]"
                            + (c.getExpireDate() == null ? "（长期有效）" : "（有效期至 " + c.getExpireDate() + "）")
                            + " - 通过");
                } else {
                    LocalDate latest = held.stream()
                            .map(VolunteerCertificate::getExpireDate)
                            .filter(d -> d != null)
                            .max(LocalDate::compareTo)
                            .orElse(null);
                    certCheck.add("证书 [" + cert + "]"
                            + (latest == null ? "" : "（已于 " + latest + " 到期）")
                            + " - 已过期");
                    anyCertExpired = true;
                    allPass = false;
                }
            }
        }
        result.setCertExpired(anyCertExpired);

        String hoursCheck;
        if (position.getRequiredHours() != null && position.getRequiredHours() > 0) {
            BigDecimal requiredHours = new BigDecimal(position.getRequiredHours());
            if (volunteer.getTotalHours() != null && volunteer.getTotalHours().compareTo(requiredHours) >= 0) {
                hoursCheck = "服务时长 [" + volunteer.getTotalHours() + "/" + requiredHours + "小时] - 通过";
            } else {
                hoursCheck = "服务时长 [" + volunteer.getTotalHours() + "/" + requiredHours + "小时] - 未满足";
                allPass = false;
            }
        } else {
            hoursCheck = "服务时长 - 无要求";
        }

        result.setPass(allPass);
        result.setSkillCheck(skillCheck);
        result.setCertCheck(certCheck);
        result.setHoursCheck(hoursCheck);

        StringBuilder message = new StringBuilder();
        if (allPass) {
            message.append("能力校验通过");
        } else {
            message.append("能力校验未通过：");
            if (activityEnded) {
                message.append(activityCheck).append("; ");
            }
            skillCheck.stream().filter(s -> s.contains("未满足")).forEach(s -> message.append(s).append("; "));
            certCheck.stream().filter(s -> s.contains("未满足") || s.contains("已过期"))
                    .forEach(s -> message.append(s).append("; "));
            if (hoursCheck.contains("未满足")) {
                message.append(hoursCheck).append("; ");
            }
        }
        result.setMessage(message.toString());

        return result;
    }

    /** 活动结束钟点已过即视为散场（status 被手工置为结束同样拦）。 */
    public boolean isActivityEnded(Activity activity) {
        if (activity == null) {
            return true;
        }
        if (activity.getStatus() != null && activity.getStatus() == 0) {
            return true;
        }
        LocalDateTime endTime = activity.getEndTime();
        return endTime != null && !LocalDateTime.now().isBefore(endTime);
    }

    /** 该志愿者在该岗位下是否有必备证书已过有效期（列表涂过期色用，不看技能/时长）。 */
    public boolean hasExpiredRequiredCertificate(Long volunteerId, Position position) {
        if (position == null || !StringUtils.hasText(position.getRequiredCertificates())) {
            return false;
        }
        List<VolunteerCertificate> held = volunteerCertificateRepository.findByVolunteerId(volunteerId);
        Map<String, List<VolunteerCertificate>> certMap = held.stream()
                .collect(Collectors.groupingBy(VolunteerCertificate::getCertName));
        LocalDate today = LocalDate.now();
        for (String raw : position.getRequiredCertificates().split(",")) {
            String cert = raw.trim();
            if (cert.isEmpty()) {
                continue;
            }
            List<VolunteerCertificate> list = certMap.get(cert);
            if (list != null && !list.isEmpty()
                    && list.stream().noneMatch(c -> c.getExpireDate() == null || !c.getExpireDate().isBefore(today))) {
                return true;
            }
        }
        return false;
    }

    public String validateAndGetJson(Long volunteerId, Long positionId) {
        CapabilityCheckResult result = validate(volunteerId, positionId);
        return JSON.toJSONString(result);
    }
}
