package com.volunteer.controller;

import com.volunteer.dto.response.ApiResponse;
import com.volunteer.entity.Activity;
import com.volunteer.repository.ActivityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    @Autowired
    private ActivityRepository activityRepository;

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
        return ApiResponse.success(activityRepository.save(activity));
    }

    @PutMapping("/{id}")
    public ApiResponse<Activity> updateActivity(@PathVariable Long id, @RequestBody Activity activity) {
        return activityRepository.findById(id)
                .map(existing -> {
                    existing.setName(activity.getName());
                    existing.setDescription(activity.getDescription());
                    existing.setStartTime(activity.getStartTime());
                    existing.setEndTime(activity.getEndTime());
                    existing.setLocation(activity.getLocation());
                    existing.setStatus(activity.getStatus());
                    return ApiResponse.success(activityRepository.save(existing));
                })
                .orElse(ApiResponse.error(404, "活动不存在"));
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