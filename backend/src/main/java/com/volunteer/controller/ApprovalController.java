package com.volunteer.controller;

import com.volunteer.dto.request.ApprovalRequest;
import com.volunteer.dto.response.ApiResponse;
import com.volunteer.dto.response.ApprovalActionResult;
import com.volunteer.entity.ApprovalFlow;
import com.volunteer.service.ApprovalFlowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    @Autowired
    private ApprovalFlowService approvalFlowService;

    @GetMapping("/registration/{registrationId}")
    public ApiResponse<List<ApprovalFlow>> getApprovalFlows(@PathVariable Long registrationId) {
        return ApiResponse.success(approvalFlowService.getApprovalFlows(registrationId));
    }

    @GetMapping("/registration/{registrationId}/current")
    public ApiResponse<ApprovalFlow> getCurrentApprovalFlow(@PathVariable Long registrationId) {
        ApprovalFlow flow = approvalFlowService.getCurrentApprovalFlow(registrationId);
        if (flow != null) {
            return ApiResponse.success(flow);
        }
        return ApiResponse.error(404, "当前审批节点不存在");
    }

    @PostMapping("/approve")
    public ApiResponse<ApprovalActionResult> approve(@RequestBody ApprovalRequest request) {
        return ApiResponse.success(approvalFlowService.approve(request));
    }

    @PostMapping("/pass")
    public ApiResponse<ApprovalActionResult> pass(@RequestBody ApprovalRequest request) {
        request.setAction(1);
        return ApiResponse.success(approvalFlowService.approve(request));
    }

    @PostMapping("/reject")
    public ApiResponse<ApprovalActionResult> reject(@RequestBody ApprovalRequest request) {
        request.setAction(2);
        return ApiResponse.success(approvalFlowService.approve(request));
    }

    @PostMapping("/return")
    public ApiResponse<ApprovalActionResult> returnBack(@RequestBody ApprovalRequest request) {
        request.setAction(3);
        return ApiResponse.success(approvalFlowService.approve(request));
    }
}
