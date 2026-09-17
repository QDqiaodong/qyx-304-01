package com.volunteer.service;

import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.entity.Activity;
import com.volunteer.entity.Position;
import com.volunteer.entity.Registration;
import com.volunteer.entity.VolunteerCertificate;
import com.volunteer.enums.ApprovalStatus;
import com.volunteer.enums.BlockReason;
import com.volunteer.repository.ActivityRepository;
import com.volunteer.repository.PositionRepository;
import com.volunteer.repository.RegistrationRepository;
import com.volunteer.repository.VolunteerCertificateRepository;
import com.volunteer.util.GateRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 证件有效期写入。
 *
 * 全局加锁顺序约定：活动 → 岗位 → 报名(按 id 升序) → 证件；任何事务都不持证件锁等报名锁。
 * 本事务：先快照读证件确定持证人（volunteerId 不可改）→ 按 id 升序锁该持证人全部
 * 在途/已批报名 → 最后锁证件行（current read 取已提交最新值）。与「通过 / 门槛重检 /
 * 活动重检 / 重提」的「报名 → 证件」同序，同秒叠提交账上只许一种结局：
 *   - 改后已过期：在途单通过不成立、立即让出名额；
 *   - 改后仍/重新有效：被「证件时效」闸门卡住的单回到被卡前节点。
 */
@Service
public class CertificateService {

    private static final List<Integer> RECHECK_STATUSES =
            List.of(ApprovalStatus.PENDING.getCode(),
                    ApprovalStatus.APPROVED.getCode(),
                    ApprovalStatus.COMPLETED.getCode(),
                    ApprovalStatus.CHECK_FAILED.getCode());

    @Autowired
    private VolunteerCertificateRepository certificateRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private CapabilityValidationService capabilityValidationService;

    @Autowired
    private RegistrationRecheckSupport recheckSupport;

    @Transactional
    public VolunteerCertificate updateCertificate(Long id, VolunteerCertificate input) {
        // 1) 快照读：仅确定持证人（不持锁，volunteerId 不允许通过本接口改）
        VolunteerCertificate identified = certificateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("证书不存在"));
        Long volunteerId = identified.getVolunteerId();

        // 2) 按 id 升序锁该持证人全部在途/已批报名；拿到这些行后别的事务无法先锁证件
        List<Registration> registrations = registrationRepository
                .findByVolunteerIdAndStatusInForUpdate(volunteerId, RECHECK_STATUSES);

        // 3) 报名锁齐后才锁证件行；current read 读到的一定是已提交最新值
        VolunteerCertificate existing = certificateRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("证书不存在"));

        String oldName = existing.getCertName();
        LocalDate oldExpire = existing.getExpireDate();

        if (input.getCertName() != null) {
            existing.setCertName(input.getCertName());
        }
        existing.setCertNo(input.getCertNo());
        existing.setIssueDate(input.getIssueDate());
        existing.setExpireDate(input.getExpireDate());
        VolunteerCertificate saved = certificateRepository.save(existing);

        boolean validityChanged = !Objects.equals(oldExpire, saved.getExpireDate())
                || !Objects.equals(oldName, saved.getCertName());
        if (validityChanged) {
            recheckHolders(saved, registrations);
        }

        return saved;
    }

    /**
     * 报名行锁持有中逐单复核；证件 FOR UPDATE 在本事务内即重取刚写入的新有效期。
     */
    private void recheckHolders(VolunteerCertificate certificate, List<Registration> registrations) {
        LocalDate today = LocalDate.now();
        String currentName = certificate.getCertName();

        for (Registration registration : registrations) {
            Position position = positionRepository.findById(registration.getPositionId()).orElse(null);
            if (position == null || !requiresCertificate(position, currentName)) {
                continue;
            }
            Activity activity = activityRepository.findById(registration.getActivityId()).orElse(null);
            boolean ongoing = GateRules.isOngoing(activity);
            CapabilityCheckResult result = capabilityValidationService
                    .validateAgainstPosition(registration.getVolunteerId(), position, today, true);

            if (ApprovalStatus.CHECK_FAILED.getCode().equals(registration.getStatus())
                    && Boolean.TRUE.equals(result.getPass()) && ongoing) {
                recheckSupport.resumeIfCleared(registration, result, ongoing, BlockReason.CERT);
            } else {
                recheckSupport.applyLiveOutcome(registration, result, ongoing);
            }
            registrationRepository.save(registration);
        }
    }

    private boolean requiresCertificate(Position position, String certName) {
        if (!StringUtils.hasText(position.getRequiredCertificates()) || certName == null) {
            return false;
        }
        for (String raw : position.getRequiredCertificates().split(",")) {
            if (certName.equals(raw.trim())) {
                return true;
            }
        }
        return false;
    }
}
