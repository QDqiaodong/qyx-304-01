package com.volunteer.repository;

import com.volunteer.entity.ApprovalFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ApprovalFlowRepository extends JpaRepository<ApprovalFlow, Long> {
    List<ApprovalFlow> findByRegistrationIdOrderByNodeLevelAsc(Long registrationId);
    List<ApprovalFlow> findByRegistrationId(Long registrationId);
    List<ApprovalFlow> findByNodeLevel(Integer nodeLevel);

    /** 退回重提后删除旧节点记录，确保两个审批节点从头重跑 */
    @Modifying
    @Transactional
    void deleteByRegistrationId(Long registrationId);
}