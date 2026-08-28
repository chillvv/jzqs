package com.jzqs.app.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jzqs.app.customer.api.WalletAdjustRequest;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.customer.service.CustomerAssetService;
import com.jzqs.app.delivery.service.DeliveryService;
import com.jzqs.app.menu.api.MenuScheduleStatusResponse;
import com.jzqs.app.menu.service.MenuScheduleService;
import com.jzqs.app.order.api.ManualCreateOrderResponse;
import com.jzqs.app.order.api.OrderActionResponse;
import com.jzqs.app.order.service.OrderPrepService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AdminOperationsFlowTest {
    @Autowired
    private OrderPrepService orderPrepService;

    @Autowired
    private CustomerAssetService customerAssetService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private MenuScheduleService menuScheduleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate seedServeDate;

    @BeforeEach
    void resetSeedData() {
        seedServeDate = LocalDate.now().plusDays(1);
        LocalDate weekStart = seedServeDate.minusDays(seedServeDate.getDayOfWeek().getValue() - 1L);
        LocalDate weekEnd = weekStart.plusDays(6);
        jdbcTemplate.update("DELETE FROM delivery_receipts");
        jdbcTemplate.update("DELETE FROM dispatch_assignments");
        jdbcTemplate.update("DELETE FROM rider_address_bindings WHERE customer_id = 1");
        jdbcTemplate.update("DELETE FROM dispatch_area_bindings WHERE area_code = '高新区'");
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id = 9201");
        jdbcTemplate.update("DELETE FROM meal_slot_orders");
        jdbcTemplate.update("DELETE FROM daily_orders");
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id IN (1, 2, 3, 1301)");
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE id IN (1, 2, 3)");
        jdbcTemplate.update("DELETE FROM customers WHERE id IN (1, 2, 3)");
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE id > 3");
        jdbcTemplate.update("DELETE FROM menu_week_items");
        jdbcTemplate.update("DELETE FROM menu_weeks");
        jdbcTemplate.update("INSERT INTO customers (id, name, phone, customer_status, source, active) VALUES (1, '张三', '13800000001', 'FORMAL', 'ADMIN', TRUE)");
        jdbcTemplate.update("INSERT INTO customers (id, name, phone, customer_status, source, active) VALUES (2, '李四', '13800000002', 'FORMAL', 'ADMIN', TRUE)");
        jdbcTemplate.update("INSERT INTO customers (id, name, phone, customer_status, source, active) VALUES (3, '王五', '13800000003', 'FORMAL', 'ADMIN', TRUE)");
        jdbcTemplate.update("INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (1, 1, '张三', '13800000001', '高新区软件园T3', '高新区', TRUE)");
        jdbcTemplate.update("INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (2, 2, '李四', '13800000002', '地址2', '商务区', TRUE)");
        jdbcTemplate.update("INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (3, 3, '王五', '13800000003', '地址3', '商务区', TRUE)");
        jdbcTemplate.update(
            "INSERT INTO menu_weeks (id, week_start_date, week_end_date, status, published_at, created_by, published_by) VALUES (1, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP, 'test', 'test')",
            weekStart,
            weekEnd
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (1, 1, ?, ?, 'LUNCH', 'ACTIVE', '[\"香煎鸡胸肉\",\"清炒时蔬\"]', 420, '香煎鸡胸肉+清炒时蔬', '香煎鸡胸肉+清炒时蔬', 420, '少油少盐', '/assets/meal-default.jpeg', 1)",
            seedServeDate,
            seedServeDate.getDayOfWeek().getValue()
        );
        jdbcTemplate.update("INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES (1, 1, 33, 0, 20, TRUE)");
        jdbcTemplate.update("INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES (2, 2, 7, 1, 5, TRUE)");
        jdbcTemplate.update("INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES (3, 3, 33, 1, 20, TRUE)");
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (1, 1, ?, 'MINIAPP', 'DELIVERED', FALSE, CURRENT_TIMESTAMP)", seedServeDate);
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (2, 2, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)", seedServeDate);
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (3, 3, ?, 'BACKEND', 'DISPATCHING', FALSE, CURRENT_TIMESTAMP)", seedServeDate);
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (1, 1, 'LUNCH', 'LUNCH', 1, 1, '少饭，不要洋葱', 'DELIVERED', 'MINIAPP')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (2, 2, 'DINNER', 'DINNER', 1, 2, '-', 'PENDING_DISPATCH', 'MINIAPP')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (3, 3, 'LUNCH', 'LUNCH', 1, 3, '微辣', 'DISPATCHING', 'BACKEND')");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (1, 1, '骑手老周', '高新区', 'DELIVERED')");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (2, 3, '骑手小李', '商务区', 'DISPATCHING')");
    }

    @Test
    void shouldCreateCancelAndConsumeOrderThroughPersistentService() {
        jdbcTemplate.update(
            "INSERT INTO rider_profiles (id, rider_name, employment_status, auth_status, default_area_code, display_order, created_at) VALUES (9201, '后台自动派', 'ACTIVE', 'ACTIVE', '高新区', 1, CURRENT_TIMESTAMP)"
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_area_bindings (area_code, default_rider_profile_id, backup_rider_profile_id, updated_by, updated_at, keywords) VALUES ('高新区', 9201, NULL, '测试', CURRENT_TIMESTAMP, '高新区')"
        );
        jdbcTemplate.update(
            "INSERT INTO rider_address_bindings (customer_id, address_id, address_fingerprint, area_code, rider_profile_id, manually_confirmed, updated_reason, updated_at) VALUES (1, 1, '高新区软件园T3', '高新区', 9201, TRUE, 'AREA_CONFIRMED', CURRENT_TIMESTAMP)"
        );

        ManualCreateOrderResponse created = orderPrepService.manualCreate(
            1L,
            null,
            "LUNCH",
            "LUNCH",
            "少饭",
            "高新区软件园T3",
            "BACKEND",
            1,
            null
        );

        long orderId = created.orderId();

        assertEquals("PENDING_DISPATCH", created.status());
        assertEquals("PENDING_DISPATCH", jdbcTemplate.queryForObject("SELECT status FROM meal_slot_orders WHERE id = ?", String.class, orderId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = ?", Integer.class, orderId));
        assertEquals(
            orderId,
            jdbcTemplate.queryForObject(
                "SELECT related_order_id FROM wallet_transactions WHERE transaction_type = 'CONSUME' ORDER BY id DESC LIMIT 1",
                Long.class
            )
        );
        assertEquals(12, customerAssetService.listAssets(null, null, null, null, null).items().get(0).remainingMeals());

        OrderActionResponse cancelled = orderPrepService.cancelOrder(orderId);
        assertEquals("CANCELLED", cancelled.status());
        assertEquals(13, customerAssetService.listAssets(null, null, null, null, null).items().get(0).remainingMeals());

        ManualCreateOrderResponse createdAgain = orderPrepService.manualCreate(
            1L,
            null,
            "LUNCH",
            "LUNCH",
            "-",
            "高新区软件园T3",
            "BACKEND",
            1,
            null
        );
        long deliveryOrderId = createdAgain.orderId();

        DeliveryReceiptRecordResponse receipt = deliveryService.recordDeliveryReceipt(
            deliveryOrderId,
            "https://cos.example.com/r1.jpg",
            "已放前台",
            "2026-05-10T12:05:00",
            "2026-05-10T12:05:00",
            "2026-05-12T12:05:00"
        );
        assertEquals("DELIVERED", receipt.orderStatus());
        assertEquals(12, customerAssetService.listAssets(null, null, null, null, null).items().get(0).remainingMeals());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM delivery_receipts", Integer.class));
    }

    @Test
    void shouldAdjustWalletAndDisableMenuSchedule() {
        customerAssetService.grantMeals(1L, new WalletAdjustRequest(5, 30, "客服A", "补餐"));
        assertEquals(18, customerAssetService.listAssets(null, null, null, null, null).items().get(0).remainingMeals());

        customerAssetService.deductMeals(1L, new WalletAdjustRequest(3, 30, "客服A", "手工扣减"));
        assertEquals(15, customerAssetService.listAssets(null, null, null, null, null).items().get(0).remainingMeals());

        MenuScheduleStatusResponse disabled = menuScheduleService.disable(1L);
        assertEquals("DISABLED", disabled.status());
        assertEquals("REST", jdbcTemplate.queryForObject("SELECT slot_status FROM menu_week_items WHERE id = 1", String.class));
    }

    @Test
    void shouldMergeManualCreateOrderForSameAddressAndUseFullWidthSemicolon() {
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'PENDING_DISPATCH', merchant_remark = '少饭' WHERE id = 3");
        jdbcTemplate.update("UPDATE daily_orders SET status = 'PENDING_DISPATCH' WHERE id = 3");
        jdbcTemplate.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (1301, 3, '王五', '13800000003', '商务区创新街9号', '商务区', FALSE)"
        );
        jdbcTemplate.update("UPDATE meal_slot_orders SET address_id = 1301 WHERE id = 3");

        ManualCreateOrderResponse merged = orderPrepService.manualCreate(
            3L,
            null,
            "LUNCH",
            "LUNCH",
            "多菜",
            "商务区创新街9号",
            "BACKEND",
            2,
            seedServeDate.toString()
        );

        assertEquals("MERGED", merged.status());
        assertEquals(3L, merged.orderId());
        assertEquals(3, jdbcTemplate.queryForObject("SELECT quantity FROM meal_slot_orders WHERE id = 3", Integer.class));
        assertEquals("少饭；多菜", jdbcTemplate.queryForObject("SELECT merchant_remark FROM meal_slot_orders WHERE id = 3", String.class));
    }
}
