package com.jzqs.app.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 全项目统一时间源：强制使用 Asia/Shanghai（北京时间）。
 * 任何需要"当前时间/今天"的地方都必须经过这里，禁止再直接调用 LocalDateTime.now()/LocalDate.now()，
 * 以免部署服务器时区非东八区时导致下单时间、扣餐有效期、送达回执、营业时间判断等算错。
 */
public final class TimeUtils {

    /** 项目统一时区：北京时间（东八区）。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private TimeUtils() {
    }

    /** 当前北京时间（带时分秒）。 */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    /** 当前北京日期。 */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
