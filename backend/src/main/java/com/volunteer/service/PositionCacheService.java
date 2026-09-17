package com.volunteer.service;

import com.alibaba.fastjson.JSON;
import com.volunteer.entity.Position;
import com.volunteer.repository.PositionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class PositionCacheService {

    private static final String POSITION_CACHE_KEY = "volunteer:position:capability:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PositionRepository positionRepository;

    public void cachePositionCapability(Long positionId) {
        Position position = positionRepository.findById(positionId).orElse(null);
        if (position != null) {
            String key = POSITION_CACHE_KEY + positionId;
            // 单值覆盖写：门槛改写后缓存里只剩最新门槛，不会按旧快照校验
            redisTemplate.opsForValue().set(key, JSON.toJSONString(position), 24, TimeUnit.HOURS);
        }
    }

    public Position getPositionCapability(Long positionId) {
        String key = POSITION_CACHE_KEY + positionId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof String json && !json.isEmpty()) {
            return JSON.parseObject(json, Position.class);
        }

        Position position = positionRepository.findById(positionId).orElse(null);
        if (position != null) {
            cachePositionCapability(positionId);
        }
        return position;
    }

    public void clearCache(Long positionId) {
        redisTemplate.delete(POSITION_CACHE_KEY + positionId);
    }

    public void cacheAllPositions() {
        List<Position> positions = positionRepository.findAll();
        for (Position position : positions) {
            cachePositionCapability(position.getId());
        }
    }
}
