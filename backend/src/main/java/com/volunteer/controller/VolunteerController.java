package com.volunteer.controller;

import com.volunteer.dto.response.ApiResponse;
import com.volunteer.entity.Volunteer;
import com.volunteer.entity.VolunteerCertificate;
import com.volunteer.entity.VolunteerSkill;
import com.volunteer.repository.VolunteerCertificateRepository;
import com.volunteer.repository.VolunteerRepository;
import com.volunteer.repository.VolunteerSkillRepository;
import com.volunteer.service.TimeValiditySweepExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/volunteers")
public class VolunteerController {

    @Autowired
    private VolunteerRepository volunteerRepository;

    @Autowired
    private VolunteerSkillRepository volunteerSkillRepository;

    @Autowired
    private VolunteerCertificateRepository volunteerCertificateRepository;

    @Autowired
    private TimeValiditySweepExecutor timeValiditySweepExecutor;

    @GetMapping
    public ApiResponse<List<Volunteer>> getAllVolunteers() {
        return ApiResponse.success(volunteerRepository.findAll());
    }

    @GetMapping("/status/{status}")
    public ApiResponse<List<Volunteer>> getVolunteersByStatus(@PathVariable Integer status) {
        return ApiResponse.success(volunteerRepository.findByStatus(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getVolunteerById(@PathVariable Long id) {
        return volunteerRepository.findById(id)
                .map(volunteer -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("volunteer", volunteer);
                    result.put("skills", volunteerSkillRepository.findByVolunteerId(id));
                    result.put("certificates", volunteerCertificateRepository.findByVolunteerId(id));
                    return ApiResponse.success(result);
                })
                .orElse(ApiResponse.error(404, "志愿者不存在"));
    }

    @PostMapping
    public ApiResponse<Volunteer> createVolunteer(@RequestBody Volunteer volunteer) {
        return ApiResponse.success(volunteerRepository.save(volunteer));
    }

    @PutMapping("/{id}")
    public ApiResponse<Volunteer> updateVolunteer(@PathVariable Long id, @RequestBody Volunteer volunteer) {
        return volunteerRepository.findById(id)
                .map(existing -> {
                    existing.setName(volunteer.getName());
                    existing.setPhone(volunteer.getPhone());
                    existing.setEmail(volunteer.getEmail());
                    existing.setIdCard(volunteer.getIdCard());
                    existing.setTotalHours(volunteer.getTotalHours());
                    existing.setStatus(volunteer.getStatus());
                    return ApiResponse.success(volunteerRepository.save(existing));
                })
                .orElse(ApiResponse.error(404, "志愿者不存在"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteVolunteer(@PathVariable Long id) {
        if (volunteerRepository.existsById(id)) {
            volunteerRepository.deleteById(id);
            return ApiResponse.success(null);
        }
        return ApiResponse.error(404, "志愿者不存在");
    }

    @PostMapping("/{id}/skills")
    public ApiResponse<VolunteerSkill> addSkill(@PathVariable Long id, @RequestBody VolunteerSkill skill) {
        skill.setVolunteerId(id);
        return ApiResponse.success(volunteerSkillRepository.save(skill));
    }

    @GetMapping("/{id}/skills")
    public ApiResponse<List<VolunteerSkill>> getSkills(@PathVariable Long id) {
        return ApiResponse.success(volunteerSkillRepository.findByVolunteerId(id));
    }

    @PostMapping("/{id}/certificates")
    public ApiResponse<VolunteerCertificate> addCertificate(@PathVariable Long id, @RequestBody VolunteerCertificate certificate) {
        certificate.setVolunteerId(id);
        VolunteerCertificate saved = volunteerCertificateRepository.save(certificate);
        // 新录入证书可能是续期：持锁立即复核该志愿者相关在途/已批单，通过即恢复占编
        timeValiditySweepExecutor.settleVolunteerCertificates(id);
        return ApiResponse.success(saved);
    }

    /**
     * 修改证书（含有效期）。证书时效在报名/通过/扫描时现场判定，
     * 改到昨天即过期，待审停校验、已批不计满员；续期后下一轮扫描恢复占编。
     */
    @PutMapping("/{id}/certificates/{certId}")
    public ApiResponse<VolunteerCertificate> updateCertificate(@PathVariable Long id,
                                                                @PathVariable Long certId,
                                                                @RequestBody VolunteerCertificate input) {
        java.util.Optional<VolunteerCertificate> found = volunteerCertificateRepository.findById(certId);
        if (found.isEmpty()) {
            return ApiResponse.error(404, "证书不存在");
        }
        VolunteerCertificate existing = found.get();
        if (!existing.getVolunteerId().equals(id)) {
            return ApiResponse.error(404, "证书不存在");
        }
        if (input.getCertName() != null) {
            existing.setCertName(input.getCertName());
        }
        existing.setCertNo(input.getCertNo());
        existing.setIssueDate(input.getIssueDate());
        existing.setExpireDate(input.getExpireDate());
        VolunteerCertificate saved = volunteerCertificateRepository.save(existing);
        // 有效期改到昨天立即腾位、续期立即恢复：持锁现场复核，不用等定时扫描
        timeValiditySweepExecutor.settleVolunteerCertificates(id);
        return ApiResponse.success(saved);
    }

    @GetMapping("/{id}/certificates")
    public ApiResponse<List<VolunteerCertificate>> getCertificates(@PathVariable Long id) {
        return ApiResponse.success(volunteerCertificateRepository.findByVolunteerId(id));
    }
}