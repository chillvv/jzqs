package com.jzqs.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jzqs.app.order.api.ManualCreateOrderResponse;
import com.jzqs.app.order.api.SubscriptionBulkImportResponse;
import com.jzqs.app.order.api.SubscriptionImportItem;
import com.jzqs.app.order.service.OrderPrepService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OrderNoteSnapshotServiceTest {

    private static final long CUSTOMER_ID = 1L;
    private static final long ADDRESS_ID = 9301L;
    private static final long WALLET_ID = 9302L;

    @Autowired
    private OrderPrepService orderPrepService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetOrderSnapshotFixtures() {
        LocalDate manualDate = LocalDate.now().plusDays(5);
        LocalDate subscriptionDate = LocalDate.now().plusDays(6);

        jdbcTemplate.update("DELETE FROM order_notes WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE daily_order_id IN (SELECT id FROM daily_orders WHERE customer_id = ? AND serve_date IN (?, ?))",
            CUSTOMER_ID, manualDate, subscriptionDate);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE customer_id = ? AND serve_date IN (?, ?)",
            CUSTOMER_ID, manualDate, subscriptionDate);
        jdbcTemplate.update("DELETE FROM customer_notes WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE wallet_id = ?", WALLET_ID);
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE id = ?", WALLET_ID);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = ?", ADDRESS_ID);

        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (?, ?, '张先生', '13800000001', '高新区科技园A座8层', '高新区', TRUE)
                """,
            ADDRESS_ID,
            CUSTOMER_ID
        );
        jdbcTemplate.update("UPDATE meal_wallets SET active = FALSE WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update(
            """
                INSERT INTO meal_wallets (
                    id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active
                ) VALUES (?, ?, NULL, 50, 0, 0, TRUE)
                """,
            WALLET_ID,
            CUSTOMER_ID
        );

        jdbcTemplate.update(
            """
                INSERT INTO customer_notes (
                    customer_id, note_type, scope_type, content, start_at, end_at, is_active, display_order, created_by, updated_by
                ) VALUES
                    (?, 'USER', 'LONG_TERM', '长期少饭', NULL, NULL, TRUE, 0, 'test', 'test'),
                    (?, 'MERCHANT', 'LONG_TERM', '重点关注', NULL, NULL, TRUE, 0, 'test', 'test'),
                    (?, 'MERCHANT', 'TIME_BOXED', '周卡体验', ?, ?, TRUE, 1, 'test', 'test')
                """,
            CUSTOMER_ID,
            CUSTOMER_ID,
            CUSTOMER_ID,
            Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
            Timestamp.valueOf(LocalDateTime.now().plusDays(7))
        );
    }

    @Test
    void shouldWriteSnapshotNotesForManualCreate() {
        ManualCreateOrderResponse result = orderPrepService.manualCreate(
            CUSTOMER_ID,
            ADDRESS_ID,
            "LUNCH",
            "LUNCH",
            "本次不要辣",
            "高新区科技园A座8层",
            "BACKEND",
            1,
            LocalDate.now().plusDays(5).toString()
        );

        long orderId = result.orderId();
        List<String> orderNotes = jdbcTemplate.query(
            "SELECT content FROM order_notes WHERE meal_slot_order_id = ? ORDER BY id",
            (rs, rowNum) -> rs.getString("content"),
            orderId
        );

        assertEquals(
            List.of("长期少饭", "本次不要辣", "重点关注", "周卡体验"),
            orderNotes
        );

        List<String> sourceAndScope = jdbcTemplate.query(
            "SELECT CONCAT(source_type, ':', scope_type) AS value FROM order_notes WHERE meal_slot_order_id = ? ORDER BY id",
            (rs, rowNum) -> rs.getString("value"),
            orderId
        );
        assertEquals(
            List.of(
                "CUSTOMER_PROFILE:SNAPSHOT",
                "SUBSCRIPTION_DEFAULT:SNAPSHOT",
                "MERCHANT_PROFILE:SNAPSHOT",
                "MERCHANT_TIME_BOXED:SNAPSHOT"
            ),
            sourceAndScope
        );
    }

    @Test
    void shouldWriteSnapshotNotesForSubscriptionImport() {
        SubscriptionBulkImportResponse result = orderPrepService.bulkImportSubscription(
            LocalDate.now().plusDays(6).toString(),
            List.of(new SubscriptionImportItem(CUSTOMER_ID, "DINNER", "DINNER", ADDRESS_ID, "固定订餐默认备注"))
        );

        assertEquals(1, result.successCount());

        Long orderId = jdbcTemplate.queryForObject(
            """
                SELECT mso.id
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE do.customer_id = ?
                  AND do.serve_date = ?
                  AND mso.meal_period = 'DINNER'
                ORDER BY mso.id DESC
                LIMIT 1
                """,
            Long.class,
            CUSTOMER_ID,
            LocalDate.now().plusDays(6)
        );

        List<String> orderNotes = jdbcTemplate.query(
            "SELECT content FROM order_notes WHERE meal_slot_order_id = ? ORDER BY id",
            (rs, rowNum) -> rs.getString("content"),
            orderId
        );

        assertEquals(
            List.of("长期少饭", "固定订餐默认备注", "重点关注", "周卡体验"),
            orderNotes
        );

        List<String> sourceAndScope = jdbcTemplate.query(
            "SELECT CONCAT(source_type, ':', scope_type) AS value FROM order_notes WHERE meal_slot_order_id = ? ORDER BY id",
            (rs, rowNum) -> rs.getString("value"),
            orderId
        );
        assertEquals(
            List.of(
                "CUSTOMER_PROFILE:SNAPSHOT",
                "SUBSCRIPTION_DEFAULT:SNAPSHOT",
                "MERCHANT_PROFILE:SNAPSHOT",
                "MERCHANT_TIME_BOXED:SNAPSHOT"
            ),
            sourceAndScope
        );
    }

    @Test
    void shouldKeepSuccessfulItemsWhenSubscriptionImportHasFailures() {
        long failedCustomerId = 9908L;
        LocalDate serveDate = LocalDate.now().plusDays(7);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE daily_order_id IN (SELECT id FROM daily_orders WHERE customer_id = ? AND serve_date = ?)", failedCustomerId, serveDate);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE customer_id = ? AND serve_date = ?", failedCustomerId, serveDate);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE customer_id = ?", failedCustomerId);
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE customer_id = ?", failedCustomerId);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", failedCustomerId);

        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active) VALUES (?, '失败客户', '13900009908', 'BACKEND', TRUE)",
            failedCustomerId
        );
        jdbcTemplate.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (?, ?, '失败客户', '13900009908', '测试失败地址', '高新区', TRUE)",
            failedCustomerId,
            failedCustomerId
        );
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active) VALUES (?, ?, NULL, 0, 0, 0, TRUE)",
            failedCustomerId,
            failedCustomerId
        );

        SubscriptionBulkImportResponse result = orderPrepService.bulkImportSubscription(
            serveDate.toString(),
            List.of(
                new SubscriptionImportItem(CUSTOMER_ID, "DINNER", "DINNER", ADDRESS_ID, "成功备注"),
                new SubscriptionImportItem(failedCustomerId, "LUNCH", "LUNCH", failedCustomerId, "失败备注")
            )
        );

        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertEquals(failedCustomerId, result.failures().get(0).customerId());

        Integer successOrders = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE do.customer_id = ?
                  AND do.serve_date = ?
                  AND mso.meal_period = 'DINNER'
                """,
            Integer.class,
            CUSTOMER_ID,
            serveDate
        );
        assertEquals(1, successOrders);
    }
}
