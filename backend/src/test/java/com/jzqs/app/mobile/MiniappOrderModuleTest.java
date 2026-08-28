package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.jzqs.app.common.realtime.RealtimeEvent;
import com.jzqs.app.common.realtime.TransactionalRealtimePublisher;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.mobile.api.MobileCreateOrderResponse;
import com.jzqs.app.order.service.OrderNoteSnapshotService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(properties = {
    "app.mobile.self-order-cutoff=23:59:59"
})
class MiniappOrderModuleTest {
    private static final long CUSTOMER_ID = 9811L;
    private static final long DEFAULT_ADDRESS_ID = 9811L;
    private static final long WALLET_ID = 9811L;
    private static final long DAILY_ORDER_ID = 9811L;
    private static final long MERGE_ORDER_ID = 9811L;

    @Autowired
    private MiniappOrderModule miniappOrderModule;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DispatchService dispatchService;

    @MockBean
    private OrderNoteSnapshotService orderNoteSnapshotService;

    @MockBean
    private TransactionalRealtimePublisher realtimeEventPublisher;

    @BeforeEach
    void resetSeedData() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate weekStart = tomorrow.minusDays(tomorrow.getDayOfWeek().getValue() - 1L);
        LocalDate weekEnd = weekStart.plusDays(6);

        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE meal_slot_order_id >= ?", MERGE_ORDER_ID);
        jdbcTemplate.update("DELETE FROM delivery_receipts WHERE meal_slot_order_id >= ?", MERGE_ORDER_ID);
        jdbcTemplate.update("DELETE FROM order_notes WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE wallet_id = ?", WALLET_ID);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id >= ?", MERGE_ORDER_ID);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id >= ?", DAILY_ORDER_ID);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE id = ?", WALLET_ID);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", CUSTOMER_ID);
        // 接单开关打开 + 下单窗口「整天开放」（openTime == cutoffTime → isOpen 恒 true）。
        // 否则凌晨 00:00-08:00 跑会被「当前自助下单已截止」拦截（时间依赖脆弱性）。
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
            "INSERT INTO menu_weeks (id, week_start_date, week_end_date, status, published_at, created_by, published_by) VALUES (9811, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP, 'test', 'test')",
            weekStart,
            weekEnd
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (9811, 9811, ?, ?, 'LUNCH', 'ACTIVE', '[\"香煎鸡胸肉\"]', 420, '香煎鸡胸肉', '香煎鸡胸肉', 420, '-', '/assets/meal-default.jpeg', 1)",
            tomorrow,
            tomorrow.getDayOfWeek().getValue()
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (9812, 9811, ?, ?, 'DINNER', 'ACTIVE', '[\"清蒸海鲈鱼\"]', 350, '清蒸海鲈鱼', '清蒸海鲈鱼', 350, '-', '/assets/meal-default.jpeg', 2)",
            tomorrow,
            tomorrow.getDayOfWeek().getValue()
        );

        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status) VALUES (?, '小程序模块客户9811', '13900009811', 'MINIAPP', TRUE, 'FORMAL')",
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (
                    ?, ?, '小程序模块客户9811', '13900009811', '高新区研发园一期', '高新区', TRUE
                )
                """,
            DEFAULT_ADDRESS_ID,
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_wallets (
                    id, customer_id, total_meals, reserved_meals, consumed_meals, active, opened_at, expired_at, last_adjusted_at
                ) VALUES (
                    ?, ?, 20, 0, 0, TRUE, CURRENT_TIMESTAMP, TIMESTAMPADD(DAY, 30, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP
                )
                """,
            WALLET_ID,
            CUSTOMER_ID
        );
    }

    @Test
    @Transactional
    void shouldMergeExistingMiniappOrderAndPublishCustomerEvents() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (?, ?, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)",
            DAILY_ORDER_ID,
            CUSTOMER_ID,
            tomorrow
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (?, ?, 'LUNCH', 'LUNCH', 1, ?, '少饭', '少饭', 'PENDING_DISPATCH', 'MINIAPP')
                """,
            MERGE_ORDER_ID,
            DAILY_ORDER_ID,
            DEFAULT_ADDRESS_ID
        );

        MobileCreateOrderResponse result = miniappOrderModule.createOrder(
            CUSTOMER_ID,
            tomorrow.toString(),
            "LUNCH",
            "高新区研发园一期",
            "不要辣"
        );

        assertEquals("MERGED", result.status());
        assertEquals(MERGE_ORDER_ID, result.orderId());

        Map<String, Object> order = jdbcTemplate.queryForMap(
            "SELECT quantity, note, user_note FROM meal_slot_orders WHERE id = ?",
            MERGE_ORDER_ID
        );
        assertEquals(2, ((Number) order.get("quantity")).intValue());
        assertEquals("少饭；不要辣", order.get("note"));
        assertEquals("少饭；不要辣", order.get("user_note"));
        assertEquals(
            1,
            jdbcTemplate.queryForObject("SELECT consumed_meals FROM meal_wallets WHERE id = ?", Integer.class, WALLET_ID)
        );
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id = ? AND related_order_id = ?",
                Integer.class,
                WALLET_ID,
                MERGE_ORDER_ID
            )
        );

        verify(orderNoteSnapshotService).writeOrderSnapshot(
            eq(MERGE_ORDER_ID),
            eq(CUSTOMER_ID),
            eq("小程序"),
            eq("少饭；不要辣"),
            isNull(),
            eq(List.of()),
            any(LocalDateTime.class)
        );
        verify(dispatchService).autoAssignPendingOrders("LUNCH");

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher, times(3)).publish(eventCaptor.capture());
        List<RealtimeEvent> events = eventCaptor.getAllValues();
        // 远程实现：dispatch.queue.changed + customer.order.changed + customer.wallet.changed
        assertEquals(List.of("dispatch.queue.changed", "customer.order.changed", "customer.wallet.changed"), events.stream().map(RealtimeEvent::eventType).toList());
        assertEquals(CUSTOMER_ID, ((Number) events.get(1).payload().get("customerId")).longValue());
        assertEquals(MERGE_ORDER_ID, ((Number) events.get(0).payload().get("orderId")).longValue());
    }

    @Test
    @Transactional
    void shouldKeepCreateOrderSuccessfulWhenSnapshotDispatchAndRealtimeFail() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        doThrow(new IllegalStateException("snapshot exploded"))
            .when(orderNoteSnapshotService)
            .writeOrderSnapshot(anyLong(), anyLong(), anyString(), any(), any(), any(), any(LocalDateTime.class));
        doThrow(new IllegalStateException("dispatch exploded"))
            .when(dispatchService)
            .autoAssignPendingOrders("DINNER");
        doThrow(new IllegalStateException("realtime exploded"))
            .when(realtimeEventPublisher)
            .publish(any(RealtimeEvent.class));

        MobileCreateOrderResponse result = assertDoesNotThrow(() -> miniappOrderModule.createOrder(
            CUSTOMER_ID,
            tomorrow.toString(),
            "DINNER",
            "高新区研发园二期",
            "加饭"
        ));

        assertEquals("PENDING_DISPATCH", result.status());
        assertNotNull(result.orderId());
        Long orderId = result.orderId();

        Map<String, Object> order = jdbcTemplate.queryForMap(
            "SELECT status, note, user_note, source_type FROM meal_slot_orders WHERE id = ?",
            orderId
        );
        assertEquals("PENDING_DISPATCH", order.get("status"));
        assertEquals("加饭", order.get("note"));
        assertEquals("加饭", order.get("user_note"));
        assertEquals("MINIAPP", order.get("source_type"));
        assertEquals(
            1,
            jdbcTemplate.queryForObject("SELECT consumed_meals FROM meal_wallets WHERE id = ?", Integer.class, WALLET_ID)
        );
        assertEquals(
            2,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_addresses WHERE customer_id = ?", Integer.class, CUSTOMER_ID)
        );
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id = ? AND related_order_id = ?",
                Integer.class,
                WALLET_ID,
                orderId
            )
        );

        verify(orderNoteSnapshotService).writeOrderSnapshot(
            eq(orderId),
            eq(CUSTOMER_ID),
            eq("小程序"),
            eq("加饭"),
            isNull(),
            eq(List.of()),
            any(LocalDateTime.class)
        );
        verify(dispatchService).autoAssignPendingOrders("DINNER");
        // dispatch.queue.changed + customer.order.changed + customer.wallet.changed（远程实现 3 个事件）
        verify(realtimeEventPublisher, times(3)).publish(any(RealtimeEvent.class));
    }

    @Test
    @Transactional
    void shouldRejectCreateOrderWhenOrderingDisabled() {
        jdbcTemplate.update("UPDATE admin_settings SET ordering_enabled = FALSE WHERE id = 1");

        BusinessException ex = assertThrows(BusinessException.class, () -> miniappOrderModule.createOrder(
            CUSTOMER_ID,
            LocalDate.now().plusDays(1).toString(),
            "LUNCH",
            "高新区研发园一期",
            "不要辣"
        ));

        assertEquals(ErrorCode.ORDERING_DISABLED, ex.getErrorCode());
        assertEquals("当前暂停接单", ex.getMessage());
    }

    @Test
    @Transactional
    void shouldNormalizeChineseMealPeriodAndBlankNoteInsideModule() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        MobileCreateOrderResponse result = miniappOrderModule.createOrder(
            CUSTOMER_ID,
            tomorrow.toString(),
            "晚餐",
            "高新区研发园二期",
            "   "
        );

        Map<String, Object> order = jdbcTemplate.queryForMap(
            "SELECT meal_period, delivery_meal_period, note, user_note FROM meal_slot_orders WHERE id = ?",
            result.orderId()
        );
        assertEquals("DINNER", order.get("meal_period"));
        assertEquals("DINNER", order.get("delivery_meal_period"));
        assertEquals("-", order.get("note"));
        assertEquals("-", order.get("user_note"));
    }
}
