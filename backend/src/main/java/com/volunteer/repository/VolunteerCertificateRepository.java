package com.volunteer.repository;

import com.volunteer.entity.VolunteerCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VolunteerCertificateRepository extends JpaRepository<VolunteerCertificate, Long> {
    List<VolunteerCertificate> findByVolunteerId(Long volunteerId);
    List<VolunteerCertificate> findByVolunteerIdAndCertNameIn(Long volunteerId, List<String> certNames);
}