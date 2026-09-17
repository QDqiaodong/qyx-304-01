package com.volunteer.util;

import com.volunteer.entity.Activity;
import com.volunteer.entity.VolunteerCertificate;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排班两道硬闸门的统一判定：
 * 1. 证件时效：岗位所需证书必须仍在有效期内（null 有效期视为长期有效）；
 * 2. 活动在办：状态为进行中且当前时间未过结束时间。
 * 报名、审批、满员计数三个入口一律走这里，避免各写一套口径。
 */
public final class GateRules {

    /** 活动进行中状态码 */
    public static final int ACTIVITY_STATUS_ONGOING = 1;

    private GateRules() {
    }

    public static boolean isOngoing(Activity activity) {
        return isOngoing(activity, LocalDateTime.now());
    }

    public static boolean isOngoing(Activity activity, LocalDateTime now) {
        if (activity == null || activity.getStatus() == null
                || activity.getStatus() != ACTIVITY_STATUS_ONGOING) {
            return false;
        }
        // 结束钟点已过即散场；结束时刻当天仍算在办
        return activity.getEndTime() == null || !now.isAfter(activity.getEndTime());
    }

    /** 证书是否在指定日期仍有效：expireDate 为空表示长期有效 */
    public static boolean isCertificateValid(VolunteerCertificate certificate, LocalDate today) {
        if (certificate == null || certificate.getExpireDate() == null) {
            return certificate != null;
        }
        return !certificate.getExpireDate().isBefore(today);
    }
}
