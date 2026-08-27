package com.jzqs.app.common.util;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 业务日期解析：返回“当前业务日期”，订单中心与看板共用，保证各页面“今日”口径一致。
 *
 * <p>口径说明：业务日期以真实的北京时间“今天”为准（{@link TimeUtils#today()}）。
 * 历史实现直接取“最近有业务发生的日期”，会导致跨天后若当天尚未产生任何订单/回执，
 * 看板“今日出餐”等指标仍停留在昨天，日期翻不过来。
 *
 * <p>因此这里仅在“真实今天没有任何业务数据、且库中存在未来日期数据”这种演示/压测数据场景下，
 * 才回退到数据中的最大日期；正常运营时永远返回真实今天。
 */
public final class BusinessDateResolver {

    private BusinessDateResolver() {
    }

    public static LocalDate resolve(JdbcTemplate jdbcTemplate) {
        LocalDate today = TimeUtils.today();
        LocalDate latest = latestDataDate(jdbcTemplate);
        // 仅当数据整体位于“今天”之后（如导入的演示数据/测试数据）时才跟随数据日期，
        // 避免正常运营场景下因当天暂无数据而把业务日期倒退回昨天。
        if (latest != null && latest.isAfter(today)) {
            return latest;
        }
        return today;
    }

    private static LocalDate latestDataDate(JdbcTemplate jdbcTemplate) {
        List<LocalDate> candidates = new ArrayList<>();
        addIfPresent(candidates, queryDate(jdbcTemplate, "SELECT MAX(CAST(delivered_at AS DATE)) FROM delivery_receipts"));
        addIfPresent(candidates, queryDate(jdbcTemplate, "SELECT MAX(CAST(created_at AS DATE)) FROM daily_orders"));
        addIfPresent(candidates, queryDate(jdbcTemplate, "SELECT MAX(CAST(created_at AS DATE)) FROM wallet_transactions"));
        addIfPresent(candidates, queryDate(jdbcTemplate, "SELECT MAX(CAST(created_at AS DATE)) FROM aftersale_cases"));
        return candidates.stream().max(LocalDate::compareTo).orElse(null);
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
