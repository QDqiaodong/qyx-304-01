package com.volunteer.repository;

import com.volunteer.entity.Activity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findByStatus(Integer status);

    /** 行锁取活动：审批通过与活动改期按同一顺序（先活动后岗位/报名）加锁 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Activity a WHERE a.id = :id")
    Optional<Activity> findByIdForUpdate(@Param("id") Long id);
}
