package com.volunteer.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * 全链路测试不依赖 Redis：岗位缓存相关调用全部空转。
 */
@TestConfiguration
public class TestMockConfig {

    @Bean
    @Primary
    public com.volunteer.service.PositionCacheService positionCacheService() {
        return mock(com.volunteer.service.PositionCacheService.class);
    }
}
