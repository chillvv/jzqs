package com.jzqs.app.common.util;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 业务日期解析：取最近有业务发生的日期作为“当前业务日期”。
 * 订单中心与看板共用，保证各页面“今日”口径一致。
 */
public final class BusinessDateResolver {

    private BusinessDateResolver() {
    }

    public static LocalDate resolve(JdbcTemplate jdbcTemplate) {
        List<LocalDate> candidates = new ArrayList<>();
        addIfPresent(candidates, queryDate(jdbcTemplate, "SELECT MAX(CAST(delivered_at AS DATE)) FROM delivery_receipts"));
        addIfPresent(candidates, queryDate(jdbcTemplate, "SELECT MAX(CAST(created_at AS DATE)) FROM daily_orders"));
        addIfPresent(candidates, queryDate(jdbcTemplate, "SELECT MAX(CAST(created_at AS DATE)) FROM wallet_transactions"));
        addIfPresent(candidates, queryDate(jdbcTemplate, "SELECT MAX(CAST(created_at AS DATE)) FROM aftersale_cases"));
        if (candidates.isEmpty()) {
            return LocalDate.now();
        }
        return candidates.stream().max(LocalDate::compareTo).orElse(LocalDate.now());
    }

    private static void addIfPresent(List<LocalDate> list, LocalDate value) {
        if (value != null) {
            list.add(value);
        }
    }

    private static LocalDate queryDate(JdbcTemplate jdbcTemplate, String sql) {
        try {
            return jdbcTemplate.queryForObject(sql, LocalDate.class);
        } catch (Exception e) {
            return null;
        }
    }
}
