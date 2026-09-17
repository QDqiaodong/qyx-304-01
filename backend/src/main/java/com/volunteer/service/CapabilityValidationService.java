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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
        result.setHoursCheck("服务时长 - 无要求");
        result.setMessage(message);
        return result;
    }

    /**
     * 按调用方给定的岗位（通常是事务内行锁读出的最新门槛）校验，
     * 保证「通过」动作看到的门槛与门槛写入事务串行后唯一确定。
     */
    public CapabilityCheckResult validateAgainstPosition(Long volunteerId, Position position) {
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
            List<VolunteerCertificate> volunteerCerts = volunteerCertificateRepository.findByVolunteerId(volunteerId);
            Set<String> certSet = volunteerCerts.stream()
                    .map(VolunteerCertificate::getCertName)
                    .collect(Collectors.toSet());

            for (String cert : requiredCerts) {
                cert = cert.trim();
                if (certSet.contains(cert)) {
                    certCheck.add("证书 [" + cert + "] - 通过");
                } else {
                    certCheck.add("证书 [" + cert + "] - 未满足");
                    allPass = false;
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
        result.setHoursCheck(hoursCheck);

        StringBuilder message = new StringBuilder();
        if (allPass) {
            message.append("能力校验通过");
        } else {
            message.append("能力校验未通过：");
            skillCheck.stream().filter(s -> s.contains("未满足")).forEach(s -> message.append(s).append("; "));
            certCheck.stream().filter(s -> s.contains("未满足")).forEach(s -> message.append(s).append("; "));
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