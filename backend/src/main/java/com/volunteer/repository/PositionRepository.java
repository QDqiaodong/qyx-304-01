package com.volunteer.repository;

import com.volunteer.entity.Position;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByActivityIdAndStatus(Long activityId, Integer status);

    /**
     * 行锁取岗位：门槛写入与通过动作按同一顺序（先岗位后报名）加锁，
     * 保证「改门槛」和「通过」并发时账上只出现一种结局。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Position p WHERE p.id = :id")
    Optional<Position> findByIdForUpdate(@Param("id") Long id);
}