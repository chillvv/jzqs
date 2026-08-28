package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jzqs.app.mobile.api.RiderOrderSequenceSaveResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class RiderOrderSequenceModuleTest {
    private static final long CUSTOMER_ID = 9841L;
    private static final long ADDRESS_ID = 9841L;
    private static final long DAILY_ORDER_ID = 9841L;
    private static final long BATCH_ID = 9841L;
    private static final long RIDER_PROFILE_ID = 9841L;
    private static final long CURRENT_ITEM_ID = 9841L;
    private static final long PENDING_ITEM_ID = 9842L;

    @Autowired
    private RiderOrderSequenceModule riderOrderSequenceModule;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSeedData() {
        jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE id IN (?, ?)", CURRENT_ITEM_ID, PENDING_ITEM_ID);
        jdbcTemplate.update("DELETE FROM dispatch_batches WHERE id = ?", BATCH_ID);
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id = ?", RIDER_PROFILE_ID);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id IN (?, ?)", CURRENT_ITEM_ID, PENDING_ITEM_ID);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id = ?", DAILY_ORDER_ID);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = ?", ADDRESS_ID);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", CUSTOMER_ID);

        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status) VALUES (?, '顺序模块客户9841', '13900009841', 'MINIAPP', TRUE, 'FORMAL')",
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (
                    ?, ?, '顺序模块客户9841', '13900009841', '高新区模块路1号', '高新区', TRUE
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
                ) VALUES (?, ?, 'LUNCH', 'LUNCH', 1, ?, '-', '-', 'DISPATCHING', 'MINIAPP')
                """,
            CURRENT_ITEM_ID,
            DAILY_ORDER_ID,
            ADDRESS_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (?, ?, 'LUNCH', 'LUNCH', 1, ?, '-', '-', 'DISPATCHING', 'MINIAPP')
                """,
            PENDING_ITEM_ID,
            DAILY_ORDER_ID,
            ADDRESS_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, display_name, phone, auth_status, employment_status, default_area_code, display_order, created_at
                ) VALUES (
                    ?, '顺序模块骑手', '顺序模块骑手', '13800009841', 'ACTIVE', 'ACTIVE', '高新区', 1, CURRENT_TIMESTAMP
                )
                """,
            RIDER_PROFILE_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_batches (
                    id, serve_date, meal_period, rider_profile_id, area_code, batch_status, total_count, delivered_count, current_sequence
                ) VALUES (
                    ?, CURRENT_DATE, 'LUNCH', ?, '高新区', 'IN_PROGRESS', 2, 0, 1
                )
                """,
            BATCH_ID,
            RIDER_PROFILE_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_batch_items (
                    id, batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted
                ) VALUES
                    (?, ?, ?, 1, 1, 'CURRENT', FALSE),
                    (?, ?, ?, 2, 2, 'PENDING', FALSE)
                """,
            CURRENT_ITEM_ID,
            BATCH_ID,
            CURRENT_ITEM_ID,
            PENDING_ITEM_ID,
            BATCH_ID,
            PENDING_ITEM_ID
        );
    }

    @Test
    @Transactional
    void shouldSaveOrderSequenceForCurrentBatch() {
        RiderOrderSequenceSaveResponse result = riderOrderSequenceModule.saveOrderSequence(
            RIDER_PROFILE_ID,
            "LUNCH",
            java.util.List.of(PENDING_ITEM_ID, CURRENT_ITEM_ID)
        );

        assertTrue(result.success());
        assertEquals("订单排序已保存", result.message());
        assertEquals(BATCH_ID, result.batchId());
        assertEquals(2, result.updatedCount());
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT current_sequence FROM dispatch_batch_items WHERE id = ?",
            Integer.class,
            PENDING_ITEM_ID
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
            "SELECT current_sequence FROM dispatch_batch_items WHERE id = ?",
            Integer.class,
            CURRENT_ITEM_ID
        ));
        assertEquals("顺序模块骑手", jdbcTemplate.queryForObject(
            "SELECT last_reordered_by FROM dispatch_batches WHERE id = ?",
            String.class,
            BATCH_ID
        ));
    }
}
