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

    /**
     * 行锁取活动：报名、通过与「活动散场扫描」按同一顺序先锁活动行，
     * 保证活动结束钟点到达的瞬间，新报名/通过与散场清场互斥，账上只出现一种结局。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Activity a WHERE a.id = :id")
    Optional<Activity> findByIdForUpdate(@Param("id") Long id);

    /** 结束钟点已过或被手工置为结束的活动（散场扫描用）。 */
    @Query("SELECT a FROM Activity a WHERE a.status = 0 OR a.endTime <= CURRENT_TIMESTAMP")
    List<Activity> findEndedActivities();
}
