package com.volunteer.repository;

import com.volunteer.entity.VolunteerSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VolunteerSkillRepository extends JpaRepository<VolunteerSkill, Long> {
    List<VolunteerSkill> findByVolunteerId(Long volunteerId);
    List<VolunteerSkill> findByVolunteerIdAndSkillNameIn(Long volunteerId, List<String> skillNames);
}