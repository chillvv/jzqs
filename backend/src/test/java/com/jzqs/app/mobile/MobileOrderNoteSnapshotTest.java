package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jzqs.app.mobile.api.MobileCreateOrderResponse;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "app.mobile.self-order-cutoff=23:59:59"
})
class MobileOrderNoteSnapshotTest {
    private static final long CUSTOMER_ID = 9911L;
    private static final long ADDRESS_ID = 9911L;
    private static final long WALLET_ID = 9911L;

    @Autowired
    private MobilePortalService mobilePortalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetFixtures() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate weekStart = tomorrow.minusDays(tomorrow.getDayOfWeek().getValue() - 1L);
        LocalDate weekEnd = weekStart.plusDays(6);

        jdbcTemplate.update("DELETE FROM order_notes WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM customer_notes WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update(
            "DELETE FROM delivery_receipts WHERE meal_slot_order_id IN (SELECT id FROM meal_slot_orders WHERE daily_order_id IN (SELECT id FROM daily_orders WHERE customer_id = ? AND serve_date = ?))",
            CUSTOMER_ID,
            tomorrow
        );
        jdbcTemplate.update(
            "DELETE FROM dispatch_assignments WHERE meal_slot_order_id IN (SELECT id FROM meal_slot_orders WHERE daily_order_id IN (SELECT id FROM daily_orders WHERE customer_id = ? AND serve_date = ?))",
            CUSTOMER_ID,
            tomorrow
        );
        jdbcTemplate.update(
            "DELETE FROM meal_slot_orders WHERE daily_order_id IN (SELECT id FROM daily_orders WHERE customer_id = ? AND serve_date = ?)",
            CUSTOMER_ID,
            tomorrow
        );
        jdbcTemplate.update("DELETE FROM daily_orders WHERE customer_id = ? AND serve_date = ?", CUSTOMER_ID, tomorrow);
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE wallet_id = ?", WALLET_ID);
        jdbcTemplate.update("DELETE FROM menu_week_items WHERE week_id = 900");
        jdbcTemplate.update("DELETE FROM menu_weeks WHERE id = 900");
        jdbcTemplate.update("DELETE FROM menu_week_items WHERE serve_date = ? AND meal_period = 'LUNCH'", tomorrow);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = ?", ADDRESS_ID);
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE id = ?", WALLET_ID);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", CUSTOMER_ID);

        jdbcTemplate.update("UPDATE admin_settings SET ordering_enabled = TRUE WHERE id = 1");
        // 下单窗口恒开：cutoff == open（避免测试在夜间/凌晨被"已截止"拦截）
        jdbcTemplate.update("UPDATE admin_settings SET night_order_cutoff_time = '23:00', night_order_open_time = '23:00' WHERE id = 1");
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status) VALUES (?, '备注快照客户9911', '13900009911', 'MINIAPP', TRUE, 'FORMAL')",
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_wallets (
                    id, customer_id, total_meals, reserved_meals, consumed_meals, active, opened_at, expired_at, last_adjusted_at
                ) VALUES (
                    ?, ?, 50, 0, 0, TRUE, CURRENT_TIMESTAMP, TIMESTAMPADD(DAY, 30, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP
                )
                """,
            WALLET_ID,
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (
                    ?, ?, '备注快照客户9911', '13900009911', '高新区科技园A座8层', '高新区', TRUE
                )
                """,
            ADDRESS_ID,
            CUSTOMER_ID
        );
        Long weekId = jdbcTemplate.query(
            "SELECT id FROM menu_weeks WHERE week_start_date = ? LIMIT 1",
            ps -> ps.setObject(1, weekStart),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (weekId == null) {
            weekId = 900L;
            jdbcTemplate.update(
                """
                    INSERT INTO menu_weeks (
                        id, week_start_date, week_end_date, status, published_at, created_by, published_by
                    ) VALUES (
                        ?, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP, 'test', 'test'
                    )
                    """,
                weekId,
                weekStart,
                weekEnd
            );
        } else {
            jdbcTemplate.update(
                """
                    UPDATE menu_weeks
                    SET week_end_date = ?, status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP, published_by = 'test'
                    WHERE id = ?
                    """,
                weekEnd,
                weekId
            );
        }
        jdbcTemplate.update(
            """
                INSERT INTO menu_week_items (
                    id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json,
                    total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order
                ) VALUES (
                    9001, ?, ?, ?, 'LUNCH', 'ACTIVE', '["香煎鸡胸肉","清炒时蔬"]',
                    420, '香煎鸡胸肉+清炒时蔬', '香煎鸡胸肉+清炒时蔬', 420, '少油少盐', '/assets/meal-default.jpeg', 1
                )
                """,
            weekId,
            tomorrow,
            tomorrow.getDayOfWeek().getValue()
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
    void shouldWriteSnapshotNotesForMiniappOrder() {
        MobileCreateOrderResponse result = mobilePortalService.createMiniappOrder(
            CUSTOMER_ID,
            LocalDate.now().plusDays(1).toString(),
            "LUNCH",
            "高新区科技园A座8层",
            "本次不要辣",
            1
        );

        Long orderId = result.orderId();
        List<String> orderNotes = jdbcTemplate.query(
            "SELECT content FROM order_notes WHERE meal_slot_order_id = ? ORDER BY id",
            (rs, rowNum) -> rs.getString("content"),
            orderId
        );

        assertEquals(List.of("长期少饭", "本次不要辣", "重点关注", "周卡体验"), orderNotes);
    }
}
