package com.volunteer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 全量装配冒烟：校验定时扫描、新行锁查询、各 Service 协作者在真实 Spring 上下文里都能装配起来。
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextSmokeTest {

    @Test
    void context_loads_with_scheduler_and_live_check_beans() {
        // 能启动即通过：@EnableScheduling、TimeValiditySweepService/Executor、
        // RegistrationLiveCheckService 与新 Repository 查询全部装配成功
    }
}
