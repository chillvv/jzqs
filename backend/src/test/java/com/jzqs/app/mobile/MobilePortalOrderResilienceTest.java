package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.mobile.api.MobileCreateOrderResponse;
import com.jzqs.app.order.service.OrderNoteSnapshotService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "app.upload-dir=${java.io.tmpdir}/jzqs-test-receipts",
    "app.mobile.self-order-cutoff=23:59:59"
})
class MobilePortalOrderResilienceTest {

    @Autowired
    private MobilePortalService mobilePortalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DispatchService dispatchService;

    @MockBean
    private OrderNoteSnapshotService orderNoteSnapshotService;

    @BeforeEach
    void resetSeedData() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        int weekdayIndex = tomorrow.getDayOfWeek().getValue();

        jdbcTemplate.update("DELETE FROM delivery_receipts");
        jdbcTemplate.update("DELETE FROM dispatch_assignments");
        jdbcTemplate.update("DELETE FROM dispatch_batch_items");
        jdbcTemplate.update("DELETE FROM dispatch_batches");
        jdbcTemplate.update("DELETE FROM rider_address_bindings");
        jdbcTemplate.update("DELETE FROM dispatch_area_bindings");
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id > 3");
        jdbcTemplate.update("DELETE FROM meal_slot_orders");
        jdbcTemplate.update("DELETE FROM daily_orders");
        jdbcTemplate.update("DELETE FROM customer_addresses");
        jdbcTemplate.update("DELETE FROM customers");
        jdbcTemplate.update("DELETE FROM meal_wallets");
        jdbcTemplate.update("DELETE FROM wallet_transactions");

        jdbcTemplate.update("INSERT INTO customers (id, name, phone, customer_status, source) VALUES (1, '张三', '13800000001', 'FORMAL', 'ADMIN')");
        jdbcTemplate.update("INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (1, 1, '张三', '13800000001', '地址1', '高新区', TRUE)");
        jdbcTemplate.update("INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES (1, 1, 33, 1, 20, TRUE)");
        jdbcTemplate.update("UPDATE admin_settings SET ordering_enabled = TRUE WHERE id = 1");
        // 下单窗口恒开：cutoff == open（避免测试在夜间/凌晨被"已截止"拦截）
        jdbcTemplate.update("UPDATE admin_settings SET night_order_cutoff_time = '23:00', night_order_open_time = '23:00' WHERE id = 1");

        jdbcTemplate.update("UPDATE menu_weeks SET status = 'ARCHIVED' WHERE id = 1");
        jdbcTemplate.update("DELETE FROM menu_week_items WHERE week_id >= 2");
        jdbcTemplate.update("DELETE FROM menu_weeks WHERE id >= 2");
        jdbcTemplate.update(
            "INSERT INTO menu_weeks (id, week_start_date, week_end_date, status, published_at, created_by, published_by) VALUES (?, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP, 'test', 'test')",
            2,
            tomorrow.minusDays(tomorrow.getDayOfWeek().getValue() - 1L),
            tomorrow.minusDays(tomorrow.getDayOfWeek().getValue() - 1L).plusDays(6)
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (21, 2, ?, ?, 'LUNCH', 'ACTIVE', '[\"香煎鸡胸肉\"]', 420, '香煎鸡胸肉', '香煎鸡胸肉', 420, '-', '/assets/meal-default.jpeg', 1)",
            tomorrow,
            weekdayIndex
        );
    }

    @Test
    void shouldKeepMiniappOrderSuccessfulWhenAutoAssignFails() {
        when(dispatchService.autoAssignPendingOrders(anyString())).thenThrow(new IllegalStateException("dispatch exploded"));

        MobileCreateOrderResponse result = assertDoesNotThrow(() -> mobilePortalService.createMiniappOrder(
            1L,
            LocalDate.now().plusDays(1).toString(),
            "LUNCH",
            "地址1",
            "",
            1
        ));

        Long orderId = result.orderId();
        assertEquals(
            "PENDING_DISPATCH",
            jdbcTemplate.queryForObject("SELECT status FROM meal_slot_orders WHERE id = ?", String.class, orderId)
        );
    }

    @Test
    void shouldKeepMiniappOrderSuccessfulWhenOrderNoteSnapshotFails() {
        doThrow(new IllegalStateException("snapshot exploded"))
            .when(orderNoteSnapshotService)
            .writeOrderSnapshot(anyLong(), anyLong(), anyString(), any(), anyList(), any());

        MobileCreateOrderResponse result = assertDoesNotThrow(() -> mobilePortalService.createMiniappOrder(
            1L,
            LocalDate.now().plusDays(1).toString(),
            "LUNCH",
            "地址1",
            "",
            1
        ));

        Long orderId = result.orderId();
        assertEquals(
            "PENDING_DISPATCH",
            jdbcTemplate.queryForObject("SELECT status FROM meal_slot_orders WHERE id = ?", String.class, orderId)
        );
    }
}
