package com.volunteer.controller;

import com.volunteer.dto.response.ApiResponse;
import com.volunteer.entity.Activity;
import com.volunteer.repository.ActivityRepository;
import com.volunteer.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityService activityService;

    @GetMapping
    public ApiResponse<List<Activity>> getAllActivities() {
        return ApiResponse.success(activityRepository.findAll());
    }

    @GetMapping("/status/{status}")
    public ApiResponse<List<Activity>> getActivitiesByStatus(@PathVariable Integer status) {
        return ApiResponse.success(activityRepository.findByStatus(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Activity> getActivityById(@PathVariable Long id) {
        return activityRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "活动不存在"));
    }

    @PostMapping
    public ApiResponse<Activity> createActivity(@RequestBody Activity activity) {
        return ApiResponse.success(activityService.createActivity(activity));
    }

    /**
     * 修改活动（含结束/改期）：同事务重检该活动全部在途报名。
     * 散场后还没批完的通过失败、已批完的不再计入满员；重新开启则放行被活动闸门卡住的单。
     */
    @PutMapping("/{id}")
    public ApiResponse<Activity> updateActivity(@PathVariable Long id, @RequestBody Activity activity) {
        try {
            return ApiResponse.success(activityService.updateActivity(id, activity));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteActivity(@PathVariable Long id) {
        if (activityRepository.existsById(id)) {
            activityRepository.deleteById(id);
            return ApiResponse.success(null);
        }
        return ApiResponse.error(404, "活动不存在");
    }
}
