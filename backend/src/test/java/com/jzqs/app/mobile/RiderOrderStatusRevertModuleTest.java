package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jzqs.app.mobile.api.RiderOrderStatusRevertResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class RiderOrderStatusRevertModuleTest {
    private static final long CUSTOMER_ID = 9851L;
    private static final long ADDRESS_ID = 9851L;
    private static final long DAILY_ORDER_ID = 9851L;
    private static final long ORDER_ID = 9851L;
    private static final long BATCH_ID = 9851L;
    private static final long BATCH_ITEM_ID = 9851L;
    private static final long RIDER_PROFILE_ID = 9851L;

    @Autowired
    private RiderOrderStatusRevertModule riderOrderStatusRevertModule;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSeedData() {
        jdbcTemplate.update("DELETE FROM delivery_receipts WHERE meal_slot_order_id = ?", ORDER_ID);
        jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE id = ?", BATCH_ITEM_ID);
        jdbcTemplate.update("DELETE FROM dispatch_batches WHERE id = ?", BATCH_ID);
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id = ?", RIDER_PROFILE_ID);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id = ?", ORDER_ID);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id = ?", DAILY_ORDER_ID);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = ?", ADDRESS_ID);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", CUSTOMER_ID);

        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status) VALUES (?, '撤回模块客户9851', '13900009851', 'MINIAPP', TRUE, 'FORMAL')",
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (
                    ?, ?, '撤回模块客户9851', '13900009851', '高新区模块路2号', '高新区', TRUE
                )
                """,
            ADDRESS_ID,
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at)
                VALUES (?, ?, ?, 'MINIAPP', 'DISPATCHING', FALSE, CURRENT_TIMESTAMP)
                """,
            DAILY_ORDER_ID,
            CUSTOMER_ID,
            LocalDate.now()
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (?, ?, 'LUNCH', 'LUNCH', 2, ?, '-', '-', 'DELIVERED', 'MINIAPP')
                """,
            ORDER_ID,
            DAILY_ORDER_ID,
            ADDRESS_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, display_name, phone, auth_status, employment_status, default_area_code, display_order, created_at
                ) VALUES (
                    ?, '撤回模块骑手', '撤回模块骑手', '13800009851', 'ACTIVE', 'ACTIVE', '高新区', 1, CURRENT_TIMESTAMP
                )
                """,
            RIDER_PROFILE_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_batches (
                    id, serve_date, meal_period, rider_profile_id, area_code, batch_status, total_count, delivered_count, current_sequence
                ) VALUES (
                    ?, CURRENT_DATE, 'LUNCH', ?, '高新区', 'IN_PROGRESS', 2, 2, 1
                )
                """,
            BATCH_ID,
            RIDER_PROFILE_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_batch_items (
                    id, batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted
                ) VALUES (
                    ?, ?, ?, 1, 1, 'DELIVERED', TRUE
                )
                """,
            BATCH_ITEM_ID,
            BATCH_ID,
            ORDER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (
                    id, meal_slot_order_id, receipt_url, receipt_note, delivered_at, visible_at, visible_to_customer
                ) VALUES (
                    ?, ?, '/uploads/revert-module.jpg', '测试回执', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE
                )
                """,
            ORDER_ID,
            ORDER_ID
        );
    }

    @Test
    @Transactional
    void shouldRevertDeliveredOrderStatusAndReceipt() {
        RiderOrderStatusRevertResponse result = riderOrderStatusRevertModule.revertOrderStatus(ORDER_ID, RIDER_PROFILE_ID);

        assertEquals(ORDER_ID, result.orderId());
        assertEquals("PENDING", result.newStatus());
        assertEquals("DISPATCHING", jdbcTemplate.queryForObject(
            "SELECT status FROM meal_slot_orders WHERE id = ?",
            String.class,
            ORDER_ID
        ));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
            "SELECT item_status FROM dispatch_batch_items WHERE id = ?",
            String.class,
            BATCH_ITEM_ID
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT delivered_count FROM dispatch_batches WHERE id = ?",
            Integer.class,
            BATCH_ID
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM delivery_receipts WHERE meal_slot_order_id = ?",
            Integer.class,
            ORDER_ID
        ));
    }
}
