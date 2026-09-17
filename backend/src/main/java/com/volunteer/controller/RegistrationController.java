package com.volunteer.controller;

import com.volunteer.dto.request.RegistrationRequest;
import com.volunteer.dto.response.ApiResponse;
import com.volunteer.dto.response.CapabilityCheckResult;
import com.volunteer.dto.response.RegistrationDetail;
import com.volunteer.entity.Registration;
import com.volunteer.service.CapabilityValidationService;
import com.volunteer.service.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private CapabilityValidationService capabilityValidationService;

    @GetMapping
    public ApiResponse<List<RegistrationDetail>> getAllRegistrations() {
        return ApiResponse.success(registrationService.getAllRegistrations());
    }

    @GetMapping("/{id}")
    public ApiResponse<RegistrationDetail> getRegistrationById(@PathVariable Long id) {
        RegistrationDetail detail = registrationService.getRegistrationDetail(id);
        if (detail != null) {
            return ApiResponse.success(detail);
        }
        return ApiResponse.error(404, "报名单不存在");
    }

    @GetMapping("/status/{status}")
    public ApiResponse<List<RegistrationDetail>> getRegistrationsByStatus(@PathVariable Integer status) {
        return ApiResponse.success(registrationService.getRegistrationsByStatus(status));
    }

    @GetMapping("/checkPass/{checkPass}")
    public ApiResponse<List<RegistrationDetail>> getRegistrationsByCheckPass(@PathVariable Integer checkPass) {
        return ApiResponse.success(registrationService.getRegistrationsByCheckPass(checkPass));
    }

    @GetMapping("/pending/{nodeLevel}")
    public ApiResponse<List<RegistrationDetail>> getPendingApprovals(@PathVariable Integer nodeLevel) {
        return ApiResponse.success(registrationService.getPendingApprovals(nodeLevel));
    }

    @PostMapping
    public ApiResponse<RegistrationDetail> createRegistration(@RequestBody RegistrationRequest request) {
        try {
            Registration registration = registrationService.createRegistration(request);
            RegistrationDetail detail = registrationService.getRegistrationDetail(registration.getId());
            return ApiResponse.success(detail);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalStateException e) {
            // 活动已结束等硬闸门拦截：新报名失败
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 退回修改后重新送审：能力校验按当前最新门槛重跑，
     * 通过则两个审批节点从组长开始全部重建，不通过则停在能力校验失败。
     */
    @PostMapping("/{id}/resubmit")
    public ApiResponse<RegistrationDetail> resubmit(@PathVariable Long id,
                                                     @RequestBody(required = false) RegistrationRequest request) {
        try {
            String applyMessage = request == null ? null : request.getApplyMessage();
            return ApiResponse.success(registrationService.resubmit(id, applyMessage));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/check")
    public ApiResponse<CapabilityCheckResult> checkCapability(@RequestBody RegistrationRequest request) {
        CapabilityCheckResult result = capabilityValidationService.validate(
                request.getVolunteerId(), request.getPositionId());
        return ApiResponse.success(result);
    }
}