package com.volunteer.repository;

import com.volunteer.entity.ApprovalFlow;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 校验占编统计与行锁 JPQL 在真实 ORM + 数据库上可执行、口径正确：
 * 退回未重提、驳回、能力校验失败、复核失败的已批人员都不计入岗位满员人数。
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase
class OccupancyQueryTest {

    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private ApprovalFlowRepository approvalFlowRepository;

    private Registration reg(Long posId, int status, Integer checkPass, Integer recheckPass) {
        Registration r = new Registration();
        r.setVolunteerId(1L);
        r.setActivityId(1L);
        r.setPositionId(posId);
        r.setStatus(status);
        r.setCheckPass(checkPass);
        r.setRecheckPass(recheckPass);
        r.setCurrentApprovalNode(ApprovalNode.LEADER.getLevel());
        return r;
    }

    @Test
    void occupied_count_excludes_returned_rejected_failed_and_recheck_failed() {
        Position p = new Position();
        p.setActivityId(1L);
        p.setName("岗位");
        p.setRequiredHours(0);
        p.setMinCount(1);
        p.setMaxCount(10);
        p.setRequirementVersion(1);
        p.setStatus(1);
        p = positionRepository.saveAndFlush(p);
        Long pid = p.getId();

        // 计入：待审批 + 校验通过，无复核
        registrationRepository.saveAndFlush(reg(pid, ApprovalStatus.PENDING.getCode(), 1, null));
        // 计入：审批完成 + 复核通过
        registrationRepository.saveAndFlush(reg(pid, ApprovalStatus.COMPLETED.getCode(), 1, 1));
        // 不计入：审批完成但复核失败（旧单保留结果，让出满员名额）
        registrationRepository.saveAndFlush(reg(pid, ApprovalStatus.COMPLETED.getCode(), 1, 0));
        // 不计入：退回修改未重提
        registrationRepository.saveAndFlush(reg(pid, ApprovalStatus.RETURNED.getCode(), 1, null));
        // 不计入：驳回
        registrationRepository.saveAndFlush(reg(pid, ApprovalStatus.REJECTED.getCode(), 1, null));
        // 不计入：门槛收紧后能力校验失败
        registrationRepository.saveAndFlush(reg(pid, ApprovalStatus.CHECK_FAILED.getCode(), 1, 0));
        // 不计入：报名时校验就没过
        registrationRepository.saveAndFlush(reg(pid, ApprovalStatus.PENDING.getCode(), 0, null));

        long occupied = registrationRepository.countOccupiedByPositionId(pid);
        assertEquals(2L, occupied);
    }

    @Test
    void grouped_count_returns_position_buckets() {
        Position p1 = positionRepository.saveAndFlush(buildPosition());
        Position p2 = positionRepository.saveAndFlush(buildPosition());
        registrationRepository.saveAndFlush(reg(p1.getId(), ApprovalStatus.PENDING.getCode(), 1, null));
        registrationRepository.saveAndFlush(reg(p2.getId(), ApprovalStatus.COMPLETED.getCode(), 1, 1));

        List<Object[]> rows = registrationRepository.countOccupiedGroupByPosition();
        long total = rows.stream().mapToLong(r -> (Long) r[1]).sum();
        assertEquals(2L, total);
    }

    @Test
    void pessimistic_lock_queries_execute() {
        Position p = positionRepository.saveAndFlush(buildPosition());
        Registration r = registrationRepository.saveAndFlush(reg(p.getId(), ApprovalStatus.PENDING.getCode(), 1, null));

        // 行锁查询必须可执行（FOR UPDATE）
        Position lockedPos = positionRepository.findByIdForUpdate(p.getId()).orElse(null);
        assertNotNull(lockedPos);
        List<Registration> locked = registrationRepository.findByPositionIdAndStatusInForUpdate(
                p.getId(), List.of(ApprovalStatus.PENDING.getCode()));
        assertEquals(1, locked.size());
        assertEquals(r.getId(), locked.get(0).getId());
    }

    @Test
    void delete_flows_by_registration_id_for_resubmit_rebuild() {
        Position p = positionRepository.saveAndFlush(buildPosition());
        Registration r = registrationRepository.saveAndFlush(reg(p.getId(), ApprovalStatus.PENDING.getCode(), 1, null));
        for (ApprovalNode node : List.of(ApprovalNode.CAPABILITY_CHECK, ApprovalNode.LEADER, ApprovalNode.MANAGER)) {
            ApprovalFlow flow = new ApprovalFlow();
            flow.setRegistrationId(r.getId());
            flow.setNodeLevel(node.getLevel());
            flow.setNodeName(node.getName());
            flow.setStatus(ApprovalStatus.PENDING.getCode());
            approvalFlowRepository.saveAndFlush(flow);
        }
        assertEquals(3, approvalFlowRepository.findByRegistrationId(r.getId()).size());

        approvalFlowRepository.deleteByRegistrationId(r.getId());
        approvalFlowRepository.flush();

        assertEquals(0, approvalFlowRepository.findByRegistrationId(r.getId()).size());
    }

    private Position buildPosition() {
        Position p = new Position();
        p.setActivityId(1L);
        p.setName("岗位");
        p.setRequiredHours(0);
        p.setMinCount(1);
        p.setMaxCount(10);
        p.setRequirementVersion(1);
        p.setStatus(1);
        return p;
    }
}
