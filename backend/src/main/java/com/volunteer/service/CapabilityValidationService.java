package com.volunteer.service;

import com.alibaba.fastjson.JSON;
import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Position;
import com.volunteer.entity.Volunteer;
import com.volunteer.entity.VolunteerCertificate;
import com.volunteer.entity.VolunteerSkill;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.VolunteerCertificateRepository;
import com.volunteer.repository.VolunteerRepository;
import com.volunteer.repository.VolunteerSkillRepository;
import com.volunteer.util.GateRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    public CapabilityCheckResult validate(Long volunteerId, Long positionId) {
        Position position = positionRepository.findById(positionId).orElse(null);
        if (position == null) {
            CapabilityCheckResult result = emptyFailResult("岗位不存在");
            return result;
        }
        return validateAgainstPosition(volunteerId, position);
    }

    private CapabilityCheckResult emptyFailResult(String message) {
        CapabilityCheckResult result = new CapabilityCheckResult();
        result.setPass(false);
        result.setSkillCheck(new ArrayList<>());
        result.setCertCheck(new ArrayList<>());
        result.setExpiredCertificates(new ArrayList<>());
        result.setHoursCheck("服务时长 - 无要求");
        result.setMessage(message);
        return result;
    }

    /**
     * 按调用方给定的岗位（通常是事务内行锁读出的最新门槛）校验，
     * 保证「通过」动作看到的门槛与门槛写入事务串行后唯一确定。
     */
    public CapabilityCheckResult validateAgainstPosition(Long volunteerId, Position position) {
        return validateAgainstPosition(volunteerId, position, LocalDate.now(), false);
    }

    /**
     * 按调用方给定的岗位（通常是事务内行锁读出的最新门槛）与「今天」校验，
     * 保证「通过」动作看到的门槛、证件有效期与门槛写入事务串行后唯一确定。
     */
    public CapabilityCheckResult validateAgainstPosition(Long volunteerId, Position position, LocalDate today) {
        return validateAgainstPosition(volunteerId, position, today, false);
    }

    /**
     * 供「通过」等持锁动作调用：lockCertificates=true 时所需证件以 FOR UPDATE 读出，
     * 与证件有效期写入事务按同一锁顺序串行，杜绝 REPEATABLE READ 快照读到旧有效期。
     * 锁顺序约定：活动 → 岗位 → 报名 → 证件。
     */
    public CapabilityCheckResult validateAgainstPosition(Long volunteerId, Position position,
                                                         LocalDate today, boolean lockCertificates) {
        CapabilityCheckResult result = new CapabilityCheckResult();
        List<String> skillCheck = new ArrayList<>();
        List<String> certCheck = new ArrayList<>();
        List<String> expiredCertificates = new ArrayList<>();

        Volunteer volunteer = volunteerRepository.findById(volunteerId).orElse(null);

        if (position == null) {
            return emptyFailResult("岗位不存在");
        }

        if (volunteer == null) {
            return emptyFailResult("志愿者不存在");
        }

        boolean allPass = true;

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

        if (StringUtils.hasText(position.getRequiredCertificates())) {
            String[] requiredCerts = position.getRequiredCertificates().split(",");
            List<String> requiredNames = new ArrayList<>();
            for (String c : requiredCerts) {
                String name = c.trim();
                if (!name.isEmpty()) {
                    requiredNames.add(name);
                }
            }
            // 持锁复核时走 FOR UPDATE：拿到的一定是已提交的最新有效期
            List<VolunteerCertificate> volunteerCerts = lockCertificates
                    ? volunteerCertificateRepository.findByVolunteerIdAndCertNameInForUpdate(
                            volunteerId, requiredNames)
                    : volunteerCertificateRepository.findByVolunteerIdAndCertNameIn(
                            volunteerId, requiredNames);
            // 同名证书取最新一条，持有即以此条时效为准（同志愿者同名证书唯一）
            Map<String, VolunteerCertificate> certMap = volunteerCerts.stream()
                    .collect(Collectors.toMap(VolunteerCertificate::getCertName, c -> c, (a, b) -> a));

            for (String cert : requiredNames) {
                VolunteerCertificate held = certMap.get(cert);
                if (held == null) {
                    certCheck.add("证书 [" + cert + "] - 未满足");
                    allPass = false;
                } else if (!GateRules.isCertificateValid(held, today)) {
                    // 证件过了有效期：待审必须停住、通过不成立
                    certCheck.add("证书 [" + cert + "] - 已过期(有效期至 " + held.getExpireDate() + ")");
                    expiredCertificates.add(cert);
                    allPass = false;
                } else {
                    certCheck.add("证书 [" + cert + "] - 通过(有效期至 " + held.getExpireDate() + ")");
                }
            }
        }

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
        result.setExpiredCertificates(expiredCertificates);
        result.setHoursCheck(hoursCheck);

        StringBuilder message = new StringBuilder();
        if (allPass) {
            message.append("能力校验通过");
        } else {
            message.append("能力校验未通过：");
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

    public String validateAndGetJson(Long volunteerId, Long positionId) {
        CapabilityCheckResult result = validate(volunteerId, positionId);
        return JSON.toJSONString(result);
    }
}