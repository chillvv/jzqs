package com.jzqs.app.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 业务日期解析的边界回归测试。
 *
 * <p>历史缺陷：业务日期取"库里最近有业务数据的那天"，导致跨天后当天若还没产生任何订单，
 * 看板"今日出餐"等指标一直停留在昨天（日期翻不过来）。这里锁死正确口径，防止回归。
 */
class BusinessDateResolverTest {

    private static final String SQL_DELIVERY = "SELECT MAX(CAST(delivered_at AS DATE)) FROM delivery_receipts";
    private static final String SQL_DAILY_ORDERS = "SELECT MAX(CAST(created_at AS DATE)) FROM daily_orders";
    private static final String SQL_WALLET_TX = "SELECT MAX(CAST(created_at AS DATE)) FROM wallet_transactions";
    private static final String SQL_AFTERSALE = "SELECT MAX(CAST(created_at AS DATE)) FROM aftersale_cases";

    /** 用最大数据日期装配一个 JdbcTemplate 假实现。 */
    private JdbcTemplate jdbcWithLatestDataDate(LocalDate latest) {
        Map<String, LocalDate> results = new HashMap<>();
        results.put(SQL_DELIVERY, latest);
        results.put(SQL_DAILY_ORDERS, latest);
        results.put(SQL_WALLET_TX, latest);
        results.put(SQL_AFTERSALE, latest);

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        results.forEach((sql, value) ->
            when(jdbcTemplate.queryForObject(eq(sql), eq(LocalDate.class))).thenReturn(value)
        );
        return jdbcTemplate;
    }

    @Test
    @DisplayName("跨天后当天零数据：业务日期必须是真实今天，不能倒退回昨天")
    void shouldReturnTodayWhenNoDataForToday() {
        LocalDate today = TimeUtils.today();
        // 模拟"最近的业务数据是昨天"，即今天刚跨天、还没有任何订单/回执
        JdbcTemplate jdbcTemplate = jdbcWithLatestDataDate(today.minusDays(1));

        LocalDate resolved = BusinessDateResolver.resolve(jdbcTemplate);

        assertThat(resolved)
            .as("当天没有业务数据时，业务日期仍应为真实今天（原缺陷会返回昨天）")
            .isEqualTo(today);
    }

    @Test
    @DisplayName("数据很旧（多日无业务）时，业务日期依然是真实今天")
    void shouldReturnTodayWhenDataIsStale() {
        LocalDate today = TimeUtils.today();
        JdbcTemplate jdbcTemplate = jdbcWithLatestDataDate(today.minusDays(30));

        assertThat(BusinessDateResolver.resolve(jdbcTemplate)).isEqualTo(today);
    }

    @Test
    @DisplayName("当天已有业务数据：业务日期为真实今天")
    void shouldReturnTodayWhenDataExistsForToday() {
        LocalDate today = TimeUtils.today();
        JdbcTemplate jdbcTemplate = jdbcWithLatestDataDate(today);

        assertThat(BusinessDateResolver.resolve(jdbcTemplate)).isEqualTo(today);
    }

    @Test
    @DisplayName("库表为空（全部为 null）时，业务日期回落为真实今天")
    void shouldReturnTodayWhenNoDataAtAll() {
        JdbcTemplate jdbcTemplate = jdbcWithLatestDataDate(null);

        assertThat(BusinessDateResolver.resolve(jdbcTemplate)).isEqualTo(TimeUtils.today());
    }

    @Test
    @DisplayName("演示/测试数据位于未来时，才跟随数据中的最大日期")
    void shouldFollowFutureDataDate() {
        LocalDate future = TimeUtils.today().plusDays(5);
        JdbcTemplate jdbcTemplate = jdbcWithLatestDataDate(future);

        assertThat(BusinessDateResolver.resolve(jdbcTemplate)).isEqualTo(future);
    }

    @Test
    @DisplayName("查询抛异常时不应崩溃，回落为真实今天")
    void shouldFallbackToTodayWhenQueryFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(), eq(LocalDate.class)))
            .thenThrow(new RuntimeException("table missing"));

        assertThat(BusinessDateResolver.resolve(jdbcTemplate)).isEqualTo(TimeUtils.today());
    }
}
