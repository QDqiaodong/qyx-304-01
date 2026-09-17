package com.volunteer.service;

import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.ApprovalFlow;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.enums.ApprovalNode;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.repository.ApprovalFlowRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验收绑定：岗位技能/证书/时长门槛加严改写时，在途单必须立即按新门槛重检，
 * 过不了停在能力校验失败并让出名额；已批完的保留审批结果但复核失败不占满员。
 */
@ExtendWith(MockitoExtension.class)
class PositionThresholdRecheckTest {

    @Mock
    private PositionRepository positionRepository;
    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private CapabilityValidationService capabilityValidationService;
    @Mock
    private PositionCacheService positionCacheService;

    @InjectMocks
    private PositionService positionService;

    private Position existing;

    @BeforeEach
    void setUp() {
        existing = new Position();
        existing.setId(10L);
        existing.setName("急救岗");
        existing.setRequiredCertificates(null);
        existing.setRequiredSkills(null);
        existing.setRequiredHours(0);
        existing.setMinCount(1);
        existing.setMaxCount(10);
        existing.setRequirementVersion(1);
        existing.setStatus(1);
    }

    private Position inputWithFirstAidCert() {
        Position input = new Position();
        input.setName("急救岗");
        input.setRequiredCertificates("急救证");
        input.setRequiredSkills(null);
        input.setRequiredHours(0);
        input.setMinCount(1);
        input.setMaxCount(10);
        input.setStatus(1);
        return input;
    }

    private Registration reg(Long id, Integer status, Integer node) {
        Registration r = new Registration();
        r.setId(id);
        r.setVolunteerId(id);
        r.setPositionId(10L);
        r.setCheckPass(1);
        r.setStatus(status);
        r.setCurrentApprovalNode(node);
        return r;
    }

    private CapabilityCheckResult failResult() {
        CapabilityCheckResult result = new CapabilityCheckResult();
        result.setPass(false);
        return result;
    }

    private CapabilityCheckResult passResult() {
        CapabilityCheckResult result = new CapabilityCheckResult();
        result.setPass(true);
        return result;
    }

    @Test
    void tightening_rechecks_inflight_and_completed_together_with_version_bump() {
        Registration inflight = reg(1L, ApprovalStatus.PENDING.getCode(), ApprovalNode.LEADER.getLevel());
        Registration completed = reg(2L, ApprovalStatus.COMPLETED.getCode(), ApprovalNode.COMPLETED.getLevel());

        when(positionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
        when(positionRepository.save(any(Position.class))).thenAnswer(i -> i.getArgument(0));
        when(registrationRepository.findByPositionIdAndStatusInForUpdate(eq(10L), any()))
                .thenReturn(List.of(inflight, completed));
        // 两个人在新「急救证」门槛下都不满足
        when(capabilityValidationService.validateAgainstPosition(eq(1L), any(Position.class))).thenReturn(failResult());
        when(capabilityValidationService.validateAgainstPosition(eq(2L), any(Position.class))).thenReturn(failResult());

        positionService.updatePosition(10L, inputWithFirstAidCert());

        // 门槛版本 +1
        assertEquals(2, existing.getRequirementVersion());

        // 在途单：停在能力校验失败，记住被卡前节点（组长），让出名额
        assertEquals(ApprovalStatus.CHECK_FAILED.getCode(), inflight.getStatus());
        assertEquals(0, inflight.getRecheckPass());
        assertEquals(ApprovalNode.CAPABILITY_CHECK.getLevel(), inflight.getCurrentApprovalNode());
        assertEquals(ApprovalNode.LEADER.getLevel(), inflight.getResumeNode());

        // 已批完的人不清退：状态仍是审批完成，但复核失败 → 不计满员
        assertEquals(ApprovalStatus.COMPLETED.getCode(), completed.getStatus());
        assertEquals(ApprovalNode.COMPLETED.getLevel(), completed.getCurrentApprovalNode());
        assertEquals(0, completed.getRecheckPass());

        verify(registrationRepository, times(2)).save(any(Registration.class));
    }

    @Test
    void loosening_restores_blocked_inflight_registration_to_pending_node() {
        // 岗位当前门槛是收紧后的「急救证」，本次编辑把它取消（放宽）
        existing.setRequiredCertificates("急救证");

        // 先前因收紧卡在能力校验失败、记录了组长节点的单子
        Registration blocked = reg(3L, ApprovalStatus.CHECK_FAILED.getCode(),
                ApprovalNode.CAPABILITY_CHECK.getLevel());
        blocked.setResumeNode(ApprovalNode.LEADER.getLevel());

        Position relaxedInput = new Position();
        relaxedInput.setName("急救岗");
        relaxedInput.setRequiredCertificates(null); // 放宽：取消急救证
        relaxedInput.setRequiredSkills(null);
        relaxedInput.setRequiredHours(0);
        relaxedInput.setMinCount(1);
        relaxedInput.setMaxCount(10);
        relaxedInput.setStatus(1);

        when(positionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
        when(positionRepository.save(any(Position.class))).thenAnswer(i -> i.getArgument(0));
        when(registrationRepository.findByPositionIdAndStatusInForUpdate(eq(10L), any()))
                .thenReturn(List.of(blocked));
        when(capabilityValidationService.validateAgainstPosition(eq(3L), any(Position.class)))
                .thenReturn(passResult());

        positionService.updatePosition(10L, relaxedInput);

        assertEquals(ApprovalStatus.PENDING.getCode(), blocked.getStatus());
        assertEquals(ApprovalNode.LEADER.getLevel(), blocked.getCurrentApprovalNode());
        assertEquals(1, blocked.getRecheckPass());
        assertNull(blocked.getResumeNode());
    }

    @Test
    void non_threshold_edit_does_not_bump_version_or_recheck() {
        Position sameThreshold = inputWithFirstAidCert();
        // 现有岗位本就要求急救证
        existing.setRequiredCertificates("急救证");
        sameThreshold.setName("急救岗-改名");

        when(positionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
        when(positionRepository.save(any(Position.class))).thenAnswer(i -> i.getArgument(0));

        positionService.updatePosition(10L, sameThreshold);

        assertEquals(1, existing.getRequirementVersion());
        verify(registrationRepository, never()).findByPositionIdAndStatusInForUpdate(any(), any());
    }
}
