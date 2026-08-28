package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.mobile.api.MobileCreateOrderResponse;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 跨端链路 E2E（连本地 docker MySQL 3307，真实 jzqs_test_* 库）。
 *
 * 与 Mockito 单测的区别：这里**不 mock** DispatchService / 事件发布 / 快照服务，
 * 走真实业务代码 + 真实数据库，用 SQL 断言跨表落库结果。
 *
 * 覆盖：
 *   1. 主链路：顾客下单 → 订单落库 + 钱包扣减 + 流水
 *   2. 复杂场景：钱包不足时不得超扣（资金红线）
 *   3. 一致性巡检：终态订单不得仍挂在活跃派单分配上（跨表状态同步铁律）
 *
 * 注：本类放在 com.jzqs.app.mobile 包，因为 MiniappOrderModule 是包级私有类，
 *     跨包无法访问（与 MiniappOrderModuleTest 同包同理）。
 * 术语遵循 CONTEXT.md：OrderStatus 终态为 DELIVERED / CANCELLED / REFUNDED。
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.mobile.self-order-cutoff=23:59:59"
})
class CrossEndFlowE2ETest {

    private static final long CUSTOMER_ID = 9822L;
    private static final long ADDRESS_ID = 9822L;
    private static final long WALLET_ID = 9822L;
    private static final long WEEK_ID = 9822L;
    private static final long LUNCH_ITEM_ID = 9822L;
    private static final long DINNER_ITEM_ID = 9823L;

    @Autowired
    private MiniappOrderModule miniappOrderModule;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSeedData() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate weekStart = tomorrow.minusDays(tomorrow.getDayOfWeek().getValue() - 1L);
        LocalDate weekEnd = weekStart.plusDays(6);

        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE meal_slot_order_id >= ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM delivery_receipts WHERE meal_slot_order_id >= ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE wallet_id = ?", WALLET_ID);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id >= ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id >= ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE id = ?", WALLET_ID);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", CUSTOMER_ID);
        // 接单开关打开，并把下单窗口设为「整天开放」：
        // MiniappOrderWindow.isOpen 在 openTime == cutoffTime 时返回 true（全天开放兜底）。
        // 不设置则默认 23:00→08:00 窗口会让凌晨/夜间跑测试被「当前自助下单已截止」拦截（时间依赖脆弱性）。
        jdbcTemplate.update(
            "UPDATE admin_settings SET ordering_enabled = TRUE, night_order_cutoff_time = '00:00', night_order_open_time = '00:00' WHERE id = 1"
        );

        jdbcTemplate.update(
            """
                DELETE mwi
                FROM menu_week_items mwi
                JOIN menu_weeks mw ON mw.id = mwi.week_id
                WHERE mw.week_start_date = ?
                """,
            weekStart
        );
        jdbcTemplate.update("DELETE FROM menu_weeks WHERE week_start_date = ?", weekStart);
        jdbcTemplate.update(
            "INSERT INTO menu_weeks (id, week_start_date, week_end_date, status, published_at, created_by, published_by) VALUES (?, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP, 'e2e', 'e2e')",
            WEEK_ID,
            weekStart,
            weekEnd
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (?, ?, ?, ?, 'LUNCH', 'ACTIVE', '[\"香煎鸡胸肉\"]', 420, '香煎鸡胸肉', '香煎鸡胸肉', 420, '-', '/assets/meal-default.jpeg', 1)",
            LUNCH_ITEM_ID,
            WEEK_ID,
            tomorrow,
            tomorrow.getDayOfWeek().getValue()
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (?, ?, ?, ?, 'DINNER', 'ACTIVE', '[\"清蒸海鲈鱼\"]', 350, '清蒸海鲈鱼', '清蒸海鲈鱼', 350, '-', '/assets/meal-default.jpeg', 2)",
            DINNER_ITEM_ID,
            WEEK_ID,
            tomorrow,
            tomorrow.getDayOfWeek().getValue()
        );

        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status) VALUES (?, '跨端E2E客户9822', '13900009822', 'MINIAPP', TRUE, 'FORMAL')",
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (?, ?, '跨端E2E客户9822', '13900009822', '高新区研发园一期', '高新区', TRUE)
                """,
            ADDRESS_ID,
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_wallets (
                    id, customer_id, total_meals, reserved_meals, consumed_meals, active, opened_at, expired_at, last_adjusted_at
                ) VALUES (?, ?, 20, 0, 0, TRUE, CURRENT_TIMESTAMP, TIMESTAMPADD(DAY, 30, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP)
                """,
            WALLET_ID,
            CUSTOMER_ID
        );
    }

    @Test
    @Transactional
    void shouldPersistOrderConsumeWalletAndWriteTransaction() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        MobileCreateOrderResponse result = miniappOrderModule.createOrder(
            CUSTOMER_ID,
            tomorrow.toString(),
            "LUNCH",
            "高新区研发园一期",
            "不要辣"
        );

        assertNotNull(result.orderId(), "下单应返回订单号");
        assertEquals("PENDING_DISPATCH", result.status());

        Map<String, Object> order = jdbcTemplate.queryForMap(
            "SELECT status, quantity, meal_period FROM meal_slot_orders WHERE id = ?",
            result.orderId()
        );
        assertEquals("PENDING_DISPATCH", order.get("status"));
        assertEquals("LUNCH", order.get("meal_period"));
        assertEquals(1, ((Number) order.get("quantity")).intValue());

        // 钱包扣减 1 餐
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT consumed_meals FROM meal_wallets WHERE id = ?",
                Integer.class,
                WALLET_ID
            )
        );
        // 生成一条扣餐流水
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id = ? AND related_order_id = ?",
                Integer.class,
                WALLET_ID,
                result.orderId()
            )
        );
    }

    @Test
    @Transactional
    void shouldNeverOversellWalletWhenBalanceIsInsufficient() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        // 钱包只剩 1 餐
        jdbcTemplate.update(
            "UPDATE meal_wallets SET total_meals = 1, consumed_meals = 0 WHERE id = ?",
            WALLET_ID
        );

        // 第一单（午餐）应成功并扣光
        MobileCreateOrderResponse first = miniappOrderModule.createOrder(
            CUSTOMER_ID,
            tomorrow.toString(),
            "LUNCH",
            "高新区研发园一期",
            ""
        );
        assertNotNull(first.orderId());

        // 第二单（晚餐）余额不足，必须被拒绝
        assertThrows(
            BusinessException.class,
            () -> miniappOrderModule.createOrder(
                CUSTOMER_ID,
                tomorrow.toString(),
                "DINNER",
                "高新区研发园二期",
                ""
            ),
            "钱包余额不足时必须拒绝下单，不得超扣"
        );

        // 红线断言：consumed_meals 绝不允许超过 total_meals
        Map<String, Object> wallet = jdbcTemplate.queryForMap(
            "SELECT total_meals, consumed_meals FROM meal_wallets WHERE id = ?",
            WALLET_ID
        );
        int total = ((Number) wallet.get("total_meals")).intValue();
        int consumed = ((Number) wallet.get("consumed_meals")).intValue();
        assertEquals(1, total);
        assertEquals(1, consumed, "只允许扣减到余额上限，不得超扣");
    }

    @Test
    @Transactional
    void shouldKeepTerminalOrdersFreeOfActiveDispatchAssignments() {
        // 一致性巡检：终态订单（DELIVERED / CANCELLED / REFUNDED）不得仍挂在活跃派单分配上。
        Integer dirty = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                  FROM meal_slot_orders o
                  JOIN dispatch_assignments da ON da.meal_slot_order_id = o.id
                 WHERE o.status IN ('DELIVERED', 'CANCELLED', 'REFUNDED')
                   AND da.status IN ('PENDING', 'ASSIGNED', 'ACCEPTED', 'PICKED_UP', 'IN_TRANSIT', 'DISPATCHED')
                """,
            Integer.class
        );
        assertEquals(
            0,
            dirty,
            "存在终态订单仍挂在活跃派单分配上（跨表状态不同步，历史事故根因）"
        );
    }
}
