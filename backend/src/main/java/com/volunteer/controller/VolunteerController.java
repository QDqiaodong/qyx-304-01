package com.volunteer.controller;

import com.volunteer.dto.response.ApiResponse;
import com.volunteer.entity.Volunteer;
import com.volunteer.entity.VolunteerCertificate;
import com.volunteer.entity.VolunteerSkill;
import com.volunteer.repository.VolunteerCertificateRepository;
import com.volunteer.repository.VolunteerRepository;
import com.volunteer.repository.VolunteerSkillRepository;
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
        return ApiResponse.success(volunteerCertificateRepository.save(certificate));
    }

    @GetMapping("/{id}/certificates")
    public ApiResponse<List<VolunteerCertificate>> getCertificates(@PathVariable Long id) {
        return ApiResponse.success(volunteerCertificateRepository.findByVolunteerId(id));
    }
}