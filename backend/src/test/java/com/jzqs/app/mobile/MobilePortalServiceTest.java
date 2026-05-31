package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.mobile.api.MobileCurrentWeekResponse;
import com.jzqs.app.mobile.api.MobileOrderItemResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MobilePortalServiceTest {

    @Autowired
    private MobilePortalService mobilePortalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSeedData() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        LocalDate weekEnd = weekStart.plusDays(6);
        int weekdayIndex = tomorrow.getDayOfWeek().getValue();

        jdbcTemplate.update("DELETE FROM notification_logs");
        jdbcTemplate.update("DELETE FROM delivery_receipts");
        jdbcTemplate.update("DELETE FROM dispatch_assignments");
        jdbcTemplate.update("DELETE FROM meal_slot_orders");
        jdbcTemplate.update("DELETE FROM daily_orders");
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE id > 3");
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id > 3");
        jdbcTemplate.update("DELETE FROM menu_week_items WHERE week_id >= 2");
        jdbcTemplate.update("DELETE FROM menu_weeks WHERE id >= 2");
        jdbcTemplate.update(
            "UPDATE menu_weeks SET status = 'ARCHIVED' WHERE id = 1"
        );
        jdbcTemplate.update(
            "INSERT INTO menu_weeks (id, week_start_date, week_end_date, status, published_at, created_by, published_by) VALUES (?, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP, 'test', 'test')",
            2,
            weekStart,
            weekEnd
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (21, 2, ?, ?, 'LUNCH', 'ACTIVE', '[\"香煎鸡胸肉\",\"清炒时蔬\"]', 420, '香煎鸡胸肉+清炒时蔬', '香煎鸡胸肉+清炒时蔬', 420, '少油少盐', '/assets/meal-default.jpeg', 1)",
            tomorrow,
            weekdayIndex
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (22, 2, ?, ?, 'DINNER', 'ACTIVE', '[\"清蒸海鲈鱼\",\"炒时蔬\"]', 350, '清蒸海鲈鱼+炒时蔬', '清蒸海鲈鱼+炒时蔬', 350, '-', '/assets/meal-default.jpeg', 2)",
            tomorrow,
            weekdayIndex
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (23, 2, ?, ?, 'LUNCH', 'ACTIVE', '[\"香煎鸡胸肉\",\"清炒时蔬\"]', 420, '香煎鸡胸肉+清炒时蔬', '香煎鸡胸肉+清炒时蔬', 420, '少油少盐', '/assets/meal-default.jpeg', 1)",
            today.plusDays(2),
            today.plusDays(2).getDayOfWeek().getValue()
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (24, 2, ?, ?, 'DINNER', 'REST', '[]', 0, '休息', '-', 0, '-', '', 2)",
            today.plusDays(2),
            today.plusDays(2).getDayOfWeek().getValue()
        );
        jdbcTemplate.update(
            "UPDATE meal_wallets SET total_meals = CASE id WHEN 1 THEN 33 WHEN 2 THEN 7 WHEN 3 THEN 33 END, " +
                "reserved_meals = CASE id WHEN 1 THEN 1 ELSE 1 END, " +
                "consumed_meals = CASE id WHEN 2 THEN 5 ELSE 20 END"
        );
        jdbcTemplate.update("UPDATE customer_addresses SET is_default = TRUE WHERE id IN (1,2,3)");
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) " +
                "VALUES (1, 1, ?, 'MINIAPP', 'DELIVERED', FALSE, CURRENT_TIMESTAMP)",
            tomorrow
        );
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) " +
                "VALUES (2, 2, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)",
            tomorrow
        );
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) " +
                "VALUES (3, 3, ?, 'BACKEND', 'DISPATCHING', FALSE, CURRENT_TIMESTAMP)",
            tomorrow
        );
        jdbcTemplate.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, status) " +
                "VALUES (1, 1, 'LUNCH', 1, 1, '少饭，不要洋葱', 'DELIVERED')"
        );
        jdbcTemplate.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, status) " +
                "VALUES (2, 2, 'DINNER', 1, 2, '-', 'PENDING_DISPATCH')"
        );
        jdbcTemplate.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, status) " +
                "VALUES (3, 3, 'LUNCH', 1, 3, '微辣', 'DISPATCHING')"
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) " +
                "VALUES (1, 1, '骑手老周', '高新区', 'DELIVERED')"
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) " +
                "VALUES (2, 3, '骑手小李', '商务区', 'DISPATCHING')"
        );
        jdbcTemplate.update(
            "INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, receipt_note, delivered_at) " +
                "VALUES (1, 1, 'https://cos.example.com/receipt-1.jpg', '已放前台', TIMESTAMP '2026-05-10 12:00:00')"
        );
    }

    @Test
    void shouldRejectDuplicateMealOrderForSameDateAndPeriod() {
        BusinessException ex = assertThrows(BusinessException.class, () -> mobilePortalService.createMiniappOrder(
            1L,
            LocalDate.now().plusDays(1).toString(),
            "LUNCH",
            "高新区科技园A座8层",
            "重复下单测试"
        ));
        assertEquals(ErrorCode.ALREADY_ORDERED, ex.getErrorCode());
    }

    @Test
    void shouldRejectCancelingOtherCustomerOrder() {
        BusinessException ex = assertThrows(BusinessException.class, () -> mobilePortalService.cancelMiniappOrder(
            "13800000001",
            2L
        ));
        assertEquals(ErrorCode.CUSTOMER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldRejectCancelingDeliveredOrderFromMiniapp() {
        BusinessException ex = assertThrows(BusinessException.class, () -> mobilePortalService.cancelMiniappOrder(
            "13800000001",
            1L
        ));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
    }

    @Test
    void shouldSetDefaultAddressForCurrentCustomerOnly() {
        jdbcTemplate.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) " +
                "VALUES (10, 1, '张先生', '13800000001', '软件园二期6号楼', '高新区', FALSE)"
        );

        Map<String, Object> result = mobilePortalService.setDefaultAddress("13800000001", 10L);

        assertEquals("DEFAULT_UPDATED", result.get("status"));
        Boolean oldDefault = jdbcTemplate.queryForObject(
            "SELECT is_default FROM customer_addresses WHERE id = 1",
            Boolean.class
        );
        Boolean newDefault = jdbcTemplate.queryForObject(
            "SELECT is_default FROM customer_addresses WHERE id = 10",
            Boolean.class
        );
        assertEquals(Boolean.FALSE, oldDefault);
        assertEquals(Boolean.TRUE, newDefault);
    }

    @Test
    void shouldReturnCurrentWeekMenuWithSevenDays() {
        MobileCurrentWeekResponse response = mobilePortalService.currentWeekMenu();
        assertEquals(7, response.days().size());
        assertEquals(
            "香煎鸡胸肉",
            response.days().stream()
                .filter(day -> !day.items().isEmpty())
                .findFirst()
                .orElseThrow()
                .items().get(0).dishItems().get(0)
        );
    }
    
    @Test
    void shouldReturnTomorrowMenuWithOrderState() {
        var response = mobilePortalService.tomorrowMenu();
        assertEquals(LocalDate.now().plusDays(1).toString(), response.serveDate());
        assertEquals(true, response.lunchItem() != null);
        assertEquals(true, response.dinnerItem() != null);
    }

    @Test
    void guestHomeShouldNotExposeLiteralNullStringsFromAdminSettings() {
        jdbcTemplate.update(
            "UPDATE admin_settings SET holiday_notice_title = NULL, holiday_notice_desc = NULL, banner_images = NULL, popup_announcement_enabled = NULL, popup_announcement_content = NULL WHERE id = 1"
        );

        var response = mobilePortalService.guestHome();

        assertEquals("", response.holidayNoticeTitle());
        assertEquals("", response.holidayNoticeDesc());
        assertEquals(List.of("../../assets/hero-new.jpg"), response.bannerImages());
        assertFalse(response.popupAnnouncementEnabled());
        assertEquals("", response.popupAnnouncementContent());
    }

    @Test
    void shouldWriteMiniappOrderRemarkIntoUserNoteField() {
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id IN (1, 2, 3)");
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id IN (1, 2, 3)");

        Map<String, Object> result = mobilePortalService.createMiniappOrder(
            1L,
            LocalDate.now().plusDays(1).toString(),
            "LUNCH",
            "高新区科技园A座8层",
            "少饭，多蔬菜"
        );

        Long orderId = ((Number) result.get("orderId")).longValue();
        Map<String, Object> order = jdbcTemplate.queryForMap(
            "SELECT user_note, note, source_type FROM meal_slot_orders WHERE id = ?",
            orderId
        );

        assertEquals("少饭，多蔬菜", order.get("user_note"));
        assertEquals("少饭，多蔬菜", order.get("note"));
        assertEquals("MINIAPP", order.get("source_type"));

        Map<String, Object> customer = jdbcTemplate.queryForMap(
            "SELECT remark FROM customers WHERE id = 1"
        );
        assertEquals("少饭，多蔬菜", customer.get("remark"));
    }

    @Test
    void shouldAutoAssignRememberedMiniappOrderImmediately() {
        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, display_name, phone, auth_status, employment_status,
                    default_area_code, assigned_by, created_at
                ) VALUES (
                    201, '骑手自动派', '自动派师傅', '13800000201', 'ACTIVE', 'ACTIVE',
                    '高新区', '测试', CURRENT_TIMESTAMP
                )
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_area_bindings (
                    area_code, keywords, default_rider_profile_id, backup_rider_profile_id, updated_by, updated_at
                ) VALUES (
                    '高新区', '高新区', 201, NULL, '测试', CURRENT_TIMESTAMP
                )
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO rider_address_bindings (
                    customer_id, address_id, address_fingerprint, area_code, rider_profile_id,
                    manually_confirmed, updated_reason, updated_at
                ) VALUES (
                    1, 1, '高新区科技园A座8层', '高新区', 201, TRUE, 'AREA_CONFIRMED', CURRENT_TIMESTAMP
                )
                """
        );

        Map<String, Object> result = mobilePortalService.createMiniappOrder(
            1L,
            LocalDate.now().plusDays(1).toString(),
            "DINNER",
            "高新区科技园A座8层",
            "自动归区测试"
        );

        Long orderId = ((Number) result.get("orderId")).longValue();
        assertEquals(
            "DISPATCHING",
            jdbcTemplate.queryForObject("SELECT status FROM meal_slot_orders WHERE id = ?", String.class, orderId)
        );
        assertEquals(
            "DISPATCHING",
            jdbcTemplate.queryForObject("SELECT status FROM dispatch_assignments WHERE meal_slot_order_id = ?", String.class, orderId)
        );
        assertEquals(
            "骑手自动派",
            jdbcTemplate.queryForObject("SELECT rider_name FROM dispatch_assignments WHERE meal_slot_order_id = ?", String.class, orderId)
        );
    }

    @Test
    void shouldKeepMiniappOrderPendingWhenRememberedAreaBindingIsInvalid() {
        jdbcTemplate.update(
            """
                INSERT INTO rider_address_bindings (
                    customer_id, address_id, address_fingerprint, area_code, rider_profile_id,
                    manually_confirmed, updated_reason, updated_at
                ) VALUES (
                    1, 1, '高新区科技园A座8层', '已失效区域', NULL, TRUE, 'AREA_CONFIRMED', CURRENT_TIMESTAMP
                )
                """
        );

        Map<String, Object> result = mobilePortalService.createMiniappOrder(
            1L,
            LocalDate.now().plusDays(1).toString(),
            "DINNER",
            "高新区科技园A座8层",
            "失效绑定测试"
        );

        Long orderId = ((Number) result.get("orderId")).longValue();
        assertEquals(
            "PENDING_DISPATCH",
            jdbcTemplate.queryForObject("SELECT status FROM meal_slot_orders WHERE id = ?", String.class, orderId)
        );
        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = ?", Integer.class, orderId)
        );
    }

    @Test
    void shouldReturnReceiptFieldsForCustomerOrders() {
        jdbcTemplate.update(
            "UPDATE delivery_receipts SET visible_at = ?, expires_at = ? WHERE id = 1",
            LocalDateTime.now().minusMinutes(5),
            LocalDateTime.now().plusHours(24)
        );

        List<MobileOrderItemResponse> items = mobilePortalService.customerOrders(1L, null).items();
        MobileOrderItemResponse deliveredOrder = items.stream()
            .filter(item -> item.id() == 1L)
            .findFirst()
            .orElseThrow();

        assertEquals("https://cos.example.com/receipt-1.jpg", deliveredOrder.receiptUrl());
        assertEquals("已放前台", deliveredOrder.receiptNote());
        assertEquals(true, deliveredOrder.receiptVisible());
    }

    @Test
    void shouldReturnOrderSourceForCustomerOrders() {
        List<MobileOrderItemResponse> items = mobilePortalService.customerOrders(3L, null).items();
        MobileOrderItemResponse backendOrder = items.stream()
            .filter(item -> item.id() == 3L)
            .findFirst()
            .orElseThrow();

        assertEquals("BACKEND", backendOrder.source());
    }

    @Test
    void shouldDeferCurrentQueueItemAndMoveItToEnd() {
        jdbcTemplate.update(
            "INSERT INTO rider_profiles (id, rider_name, employment_status, default_area_code, display_order, created_at) VALUES (101, '骑手小李', 'ACTIVE', '商务区', 1, CURRENT_TIMESTAMP)"
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_batches (id, serve_date, meal_period, rider_profile_id, area_code, batch_status, total_count, delivered_count, current_sequence) VALUES (101, ?, 'LUNCH', 101, '商务区', 'IN_PROGRESS', 2, 0, 1)",
            LocalDate.now().plusDays(1)
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_batch_items (id, batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted) VALUES (111, 101, 3, 1, 1, 'CURRENT', FALSE)"
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_batch_items (id, batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted) VALUES (112, 101, 2, 2, 2, 'PENDING', FALSE)"
        );

        Map<String, Object> result = mobilePortalService.deferRiderQueueItem("骑手小李", 111L);

        assertEquals("DEFERRED", result.get("itemStatus"));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT current_sequence FROM dispatch_batch_items WHERE id = 111", Integer.class));
        assertEquals("DEFERRED", jdbcTemplate.queryForObject("SELECT item_status FROM dispatch_batch_items WHERE id = 111", String.class));
        assertEquals("CURRENT", jdbcTemplate.queryForObject("SELECT item_status FROM dispatch_batch_items WHERE id = 112", String.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT current_sequence FROM dispatch_batches WHERE id = 101", Integer.class));
    }

    @Test
    void shouldResumeDeferredQueueItemBackToPending() {
        jdbcTemplate.update(
            "INSERT INTO rider_profiles (id, rider_name, employment_status, default_area_code, display_order, created_at) VALUES (102, '骑手小李', 'ACTIVE', '商务区', 1, CURRENT_TIMESTAMP)"
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_batches (id, serve_date, meal_period, rider_profile_id, area_code, batch_status, total_count, delivered_count, current_sequence) VALUES (102, ?, 'LUNCH', 102, '商务区', 'IN_PROGRESS', 2, 0, 1)",
            LocalDate.now().plusDays(1)
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_batch_items (id, batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted) VALUES (121, 102, 3, 2, 1, 'DEFERRED', FALSE)"
        );

        Map<String, Object> result = mobilePortalService.resumeRiderQueueItem("骑手小李", 121L);

        assertEquals("PENDING", result.get("itemStatus"));
        assertEquals("PENDING", jdbcTemplate.queryForObject("SELECT item_status FROM dispatch_batch_items WHERE id = 121", String.class));
    }

    @Test
    void shouldAdvanceQueueAfterSubmittingReceipt() {
        jdbcTemplate.update(
            "INSERT INTO rider_profiles (id, rider_name, employment_status, default_area_code, display_order, created_at) VALUES (103, '骑手小李', 'ACTIVE', '商务区', 1, CURRENT_TIMESTAMP)"
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_batches (id, serve_date, meal_period, rider_profile_id, area_code, batch_status, total_count, delivered_count, current_sequence) VALUES (103, ?, 'LUNCH', 103, '商务区', 'IN_PROGRESS', 2, 0, 1)",
            LocalDate.now().plusDays(1)
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_batch_items (id, batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted) VALUES (131, 103, 3, 1, 1, 'CURRENT', FALSE)"
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_batch_items (id, batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted) VALUES (132, 103, 2, 2, 2, 'PENDING', FALSE)"
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (103, 2, '骑手小李', '商务区', 'DISPATCHING')"
        );

        mobilePortalService.submitRiderReceipt(3L, "骑手小李", "receipt-3.jpg", "已送达", LocalDateTime.now().withNano(0).toString());

        assertEquals("DELIVERED", jdbcTemplate.queryForObject("SELECT item_status FROM dispatch_batch_items WHERE id = 131", String.class));
        assertEquals("CURRENT", jdbcTemplate.queryForObject("SELECT item_status FROM dispatch_batch_items WHERE id = 132", String.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT delivered_count FROM dispatch_batches WHERE id = 103", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT current_sequence FROM dispatch_batches WHERE id = 103", Integer.class));
    }
}
