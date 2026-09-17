package com.volunteer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 时效扫描调度入口：排班口说了算，不等岗位页的人拖到活动当晚手动清。
 *
 * 固定频率（默认 30s，app.sweep.fixed-delay-ms 可调）跑两类扫描：
 * 1. 活动已散场：新报名早已进不来，在途/已批的单立即清场腾位；
 * 2. 必备证书过了有效期（如护理资格证昨天到期）：在途停校验、通过不成立，已批不计满员。
 *
 * 事务边界在 {@link TimeValiditySweepExecutor}（Spring 代理事务，避免自调用失效）。
 */
@Service
public class TimeValiditySweepService {

    private static final Logger log = LoggerFactory.getLogger(TimeValiditySweepService.class);

    @Autowired
    private TimeValiditySweepExecutor executor;

    @Scheduled(fixedDelayString = "${app.sweep.fixed-delay-ms:30000}",
            initialDelayString = "${app.sweep.initial-delay-ms:10000}")
    public void sweep() {
        try {
            executor.sweepEndedActivities();
        } catch (Exception e) {
            log.warn("活动散场清场扫描失败: {}", e.getMessage());
        }
        try {
            executor.sweepExpiredCertificates();
        } catch (Exception e) {
            log.warn("证件有效期扫描失败: {}", e.getMessage());
        }
    }
}
