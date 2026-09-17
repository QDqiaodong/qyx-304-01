package com.volunteer.repository;

import com.volunteer.entity.Registration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    List<Registration> findByVolunteerId(Long volunteerId);
    List<Registration> findByActivityId(Long activityId);
    List<Registration> findByStatus(Integer status);
    List<Registration> findByCheckPass(Integer checkPass);
    List<Registration> findByCurrentApprovalNode(Integer currentApprovalNode);
    List<Registration> findByStatusAndCurrentApprovalNode(Integer status, Integer currentApprovalNode);

    List<Registration> findByPositionIdAndStatusIn(Long positionId, List<Integer> statuses);

    /** 行锁取报名单，必须在已持有岗位行锁的事务内调用（锁顺序：岗位 → 报名） */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Registration r WHERE r.id = :id")
    Optional<Registration> findByIdForUpdate(@Param("id") Long id);

    /**
     * 行锁批量取岗位下指定状态的报名单（门槛重检用，锁顺序：岗位 → 本批报名）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Registration r WHERE r.positionId = :positionId AND r.status IN :statuses")
    List<Registration> findByPositionIdAndStatusInForUpdate(@Param("positionId") Long positionId,
                                                            @Param("statuses") List<Integer> statuses);

    /**
     * 岗位占编人数：状态为待审批/已通过/审批完成，且有效校验通过
     * （有复核以复核为准，无复核以报名时校验为准）。
     * 退回修改未重提、驳回、能力校验失败、复核失败的已批人员都不计入。
     */
    @Query("SELECT COUNT(r) FROM Registration r WHERE r.positionId = :positionId "
            + "AND r.status IN (0, 1, 4) "
            + "AND (r.recheckPass = 1 OR (r.recheckPass IS NULL AND r.checkPass = 1))")
    long countOccupiedByPositionId(@Param("positionId") Long positionId);

    /** 一次查全部岗位的占编人数，返回 [positionId, count] 列表 */
    @Query("SELECT r.positionId, COUNT(r) FROM Registration r WHERE r.status IN (0, 1, 4) "
            + "AND (r.recheckPass = 1 OR (r.recheckPass IS NULL AND r.checkPass = 1)) "
            + "GROUP BY r.positionId")
    List<Object[]> countOccupiedGroupByPosition();
}