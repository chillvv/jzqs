package com.jzqs.app.mobile;

import java.time.LocalTime;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 小程序自助下单窗口：每晚截止时间（默认 23:00）至 明早开放时间（默认 08:00）之间不接受新订单。
 * 时间配置存储于 admin_settings（night_order_cutoff_time / night_order_open_time），
 * 供下单接口与明日菜单接口统一读取，保证前后端判定一致。
 */
final class MiniappOrderWindow {
    private static final LocalTime DEFAULT_CUTOFF_TIME = LocalTime.of(23, 0);
    private static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(8, 0);

    private MiniappOrderWindow() {
    }

    /**
     * 判定当前时间是否处于可下单窗口内。
     * 窗口语义：每晚截止时间(cutoff) 之后关闭，直到 次日 明早开放时间(open) 重新开放。
     * - cutoff == open：整天开放（兜底，避免把全天误判为关闭）；
     * - cutoff > open ：关闭窗口跨午夜，如 23:00 → 08:00，关闭时段 [23:00, 24:00) ∪ [00:00, 08:00)；
     * - cutoff < open ：关闭窗口在同一天内，如 02:00 → 06:00，关闭时段 [02:00, 06:00)。
     */
    static boolean isOpen(LocalTime now, LocalTime openTime, LocalTime cutoffTime) {
        if (openTime.equals(cutoffTime)) {
            return true;
        }
        if (openTime.isBefore(cutoffTime)) {
            // 关闭窗口跨午夜：开放时段 [open, cutoff)
            return !now.isBefore(openTime) && now.isBefore(cutoffTime);
        }
        // 关闭窗口在同一天内：开放时段 [00:00, cutoff) ∪ [open, 24:00)
        return now.isBefore(cutoffTime) || !now.isBefore(openTime);
    }

    static LocalTime cutoffTime(JdbcTemplate jdbcTemplate) {
        return loadOrDefault(jdbcTemplate, "night_order_cutoff_time", DEFAULT_CUTOFF_TIME);
    }

    static LocalTime openTime(JdbcTemplate jdbcTemplate) {
        return loadOrDefault(jdbcTemplate, "night_order_open_time", DEFAULT_OPEN_TIME);
    }

    private static LocalTime loadOrDefault(JdbcTemplate jdbcTemplate, String column, LocalTime fallback) {
        try {
            String value = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM admin_settings WHERE id = 1",
                String.class
            );
            return value == null || value.isBlank() ? fallback : LocalTime.parse(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }
}
