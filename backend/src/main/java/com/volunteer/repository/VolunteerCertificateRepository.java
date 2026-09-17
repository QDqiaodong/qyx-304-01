package com.volunteer.repository;

import com.volunteer.entity.VolunteerCertificate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VolunteerCertificateRepository extends JpaRepository<VolunteerCertificate, Long> {
    List<VolunteerCertificate> findByVolunteerId(Long volunteerId);
    List<VolunteerCertificate> findByVolunteerIdAndCertNameIn(Long volunteerId, List<String> certNames);

    /** 行锁取证件：改有效期与「通过」并发时串行，账上只许一种结局 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM VolunteerCertificate c WHERE c.id = :id")
    Optional<VolunteerCertificate> findByIdForUpdate(@Param("id") Long id);

    /** 行锁取某志愿者的岗位所需证件（通过动作持锁现场核时效用，锁顺序：岗位 → 报名 → 证件） */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM VolunteerCertificate c WHERE c.volunteerId = :volunteerId AND c.certName IN :certNames")
    List<VolunteerCertificate> findByVolunteerIdAndCertNameInForUpdate(@Param("volunteerId") Long volunteerId,
                                                                      @Param("certNames") List<String> certNames);
}
