package com.volunteer.controller;

import com.volunteer.dto.response.ApiResponse;
import com.volunteer.entity.Position;
import com.volunteer.repository.PositionRepository;
import com.volunteer.service.PositionCacheService;
import com.volunteer.service.PositionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
public class PositionController {

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private PositionCacheService positionCacheService;

    @Autowired
    private PositionService positionService;

    @GetMapping
    public ApiResponse<List<Position>> getAllPositions() {
        return ApiResponse.success(positionService.attachOccupancy(positionRepository.findAll()));
    }

    @GetMapping("/activity/{activityId}")
    public ApiResponse<List<Position>> getPositionsByActivity(@PathVariable Long activityId) {
        return ApiResponse.success(
                positionService.attachOccupancy(positionRepository.findByActivityIdAndStatus(activityId, 1)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Position> getPositionById(@PathVariable Long id) {
        Position position = positionCacheService.getPositionCapability(id);
        if (position != null) {
            return ApiResponse.success(positionService.attachOccupancy(position));
        }
        return positionRepository.findById(id)
                .map(p -> ApiResponse.success(positionService.attachOccupancy(p)))
                .orElse(ApiResponse.error(404, "岗位不存在"));
    }

    /** 岗位当前实际占编人数（退回未重提、复核失败均不计入） */
    @GetMapping("/{id}/occupied")
    public ApiResponse<java.util.Map<String, Object>> getOccupiedCount(@PathVariable Long id) {
        if (!positionRepository.existsById(id)) {
            return ApiResponse.error(404, "岗位不存在");
        }
        long occupied = positionService.getOccupiedCount(id);
        Position position = positionRepository.findById(id).orElse(null);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("positionId", id);
        data.put("occupiedCount", occupied);
        data.put("maxCount", position == null ? null : position.getMaxCount());
        data.put("full", position != null && position.getMaxCount() != null
                && position.getMaxCount() > 0 && occupied >= position.getMaxCount());
        return ApiResponse.success(data);
    }

    @PostMapping
    public ApiResponse<Position> createPosition(@RequestBody Position position) {
        return ApiResponse.success(positionService.createPosition(position));
    }

    @PutMapping("/{id}")
    public ApiResponse<Position> updatePosition(@PathVariable Long id, @RequestBody Position position) {
        try {
            return ApiResponse.success(positionService.updatePosition(id, position));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePosition(@PathVariable Long id) {
        if (positionRepository.existsById(id)) {
            positionRepository.deleteById(id);
            positionCacheService.clearCache(id);
            return ApiResponse.success(null);
        }
        return ApiResponse.error(404, "岗位不存在");
    }
}
