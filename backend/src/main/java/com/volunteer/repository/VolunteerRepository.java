package com.volunteer.repository;

import com.volunteer.entity.Volunteer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VolunteerRepository extends JpaRepository<Volunteer, Long> {
    List<Volunteer> findByStatus(Integer status);
    Optional<Volunteer> findByPhone(String phone);
    Optional<Volunteer> findByIdCard(String idCard);
}