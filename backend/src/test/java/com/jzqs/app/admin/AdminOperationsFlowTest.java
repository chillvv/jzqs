package com.jzqs.app.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jzqs.app.customer.api.WalletAdjustRequest;
import com.jzqs.app.customer.service.CustomerAssetService;
import com.jzqs.app.delivery.service.DeliveryService;
import com.jzqs.app.menu.service.MenuScheduleService;
import com.jzqs.app.order.service.OrderPrepService;
import java.util.Map;
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

    @BeforeEach
    void resetSeedData() {
        jdbcTemplate.update("DELETE FROM notification_logs");
        jdbcTemplate.update("DELETE FROM delivery_receipts");
        jdbcTemplate.update("DELETE FROM dispatch_assignments");
        jdbcTemplate.update("DELETE FROM meal_slot_orders");
        jdbcTemplate.update("DELETE FROM daily_orders");
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE id > 3");
        jdbcTemplate.update("DELETE FROM menu_week_items WHERE week_id >= 2");
        jdbcTemplate.update("DELETE FROM menu_weeks WHERE id >= 2");
        jdbcTemplate.update("UPDATE meal_wallets SET total_meals = CASE id WHEN 1 THEN 33 WHEN 2 THEN 7 WHEN 3 THEN 33 END, reserved_meals = CASE id WHEN 1 THEN 0 ELSE 1 END, consumed_meals = CASE id WHEN 2 THEN 5 ELSE 20 END");
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (1, 1, DATE '2026-05-12', 'MINIAPP', 'DELIVERED', FALSE, TIMESTAMP '2026-05-10 09:00:00')");
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (2, 2, DATE '2026-05-12', 'MINIAPP', 'PENDING_DISPATCH', FALSE, TIMESTAMP '2026-05-10 09:05:00')");
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (3, 3, DATE '2026-05-12', 'BACKEND', 'DISPATCHING', FALSE, TIMESTAMP '2026-05-10 09:10:00')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, status) VALUES (1, 1, 'LUNCH', 1, 1, '少饭，不要洋葱', 'DELIVERED')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, status) VALUES (2, 2, 'DINNER', 1, 2, '-', 'PENDING_DISPATCH')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, status) VALUES (3, 3, 'LUNCH', 1, 3, '微辣', 'DISPATCHING')");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (1, 1, '骑手老周', '高新区', 'DELIVERED')");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (2, 3, '骑手小李', '商务区', 'DISPATCHING')");
    }

    @Test
    void shouldCreateCancelAndConsumeOrderThroughPersistentService() {
        jdbcTemplate.update(
            "INSERT INTO rider_profiles (id, rider_name, employment_status, auth_status, default_area_code, display_order, created_at) VALUES (201, '后台自动派', 'ACTIVE', 'ACTIVE', '高新区', 1, CURRENT_TIMESTAMP)"
        );
        jdbcTemplate.update(
            "INSERT INTO dispatch_area_bindings (area_code, default_rider_profile_id, backup_rider_profile_id, updated_by, updated_at, keywords) VALUES ('高新区', 201, NULL, '测试', CURRENT_TIMESTAMP, '高新区')"
        );
        jdbcTemplate.update(
            "INSERT INTO rider_address_bindings (customer_id, address_id, address_fingerprint, area_code, rider_profile_id, manually_confirmed, updated_reason, updated_at) VALUES (1, 1, '高新区软件园T3', '高新区', 201, TRUE, 'AREA_CONFIRMED', CURRENT_TIMESTAMP)"
        );

        Map<String, Object> created = orderPrepService.manualCreate(1L, null, "午餐 / 香煎鸡胸肉套餐", "少饭", "高新区软件园T3", "BACKEND", 1, null);

        long orderId = ((Number) created.get("orderId")).longValue();

        assertEquals("DISPATCHING", created.get("status"));
        assertEquals("DISPATCHING", jdbcTemplate.queryForObject("SELECT status FROM meal_slot_orders WHERE id = ?", String.class, orderId));
        assertEquals("后台自动派", jdbcTemplate.queryForObject("SELECT rider_name FROM dispatch_assignments WHERE meal_slot_order_id = ?", String.class, orderId));
        assertEquals(
            orderId,
            jdbcTemplate.queryForObject(
                "SELECT related_order_id FROM wallet_transactions WHERE transaction_type = 'RESERVE' ORDER BY id DESC LIMIT 1",
                Long.class
            )
        );
        assertEquals(12, customerAssetService.listAssets(null, null, null, null, null).items().get(0).remainingMeals());

        Map<String, Object> cancelled = orderPrepService.cancelOrder(orderId);
        assertEquals("CANCELLED", cancelled.get("status"));
        assertEquals(13, customerAssetService.listAssets(null, null, null, null, null).items().get(0).remainingMeals());

        Map<String, Object> createdAgain = orderPrepService.manualCreate(1L, null, "午餐 / 香煎鸡胸肉套餐", "-", "高新区软件园T3", "BACKEND", 1, null);
        long deliveryOrderId = ((Number) createdAgain.get("orderId")).longValue();

        Map<String, Object> receipt = deliveryService.recordDeliveryReceipt(
            deliveryOrderId,
            "https://cos.example.com/r1.jpg",
            "已放前台",
            "2026-05-10T12:05:00",
            null,
            null
        );
        assertEquals("DELIVERED", receipt.get("orderStatus"));
        assertEquals(12, customerAssetService.listAssets(null, null, null, null, null).items().get(0).remainingMeals());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_logs", Integer.class));
    }

    @Test
    void shouldAdjustWalletAndDisableMenuSchedule() {
        customerAssetService.grantMeals(1L, new WalletAdjustRequest(5, "客服A", "补餐"));
        assertEquals(18, customerAssetService.listAssets(null, null, null, null, null).items().get(0).remainingMeals());

        customerAssetService.deductMeals(1L, new WalletAdjustRequest(3, "客服A", "手工扣减"));
        assertEquals(15, customerAssetService.listAssets(null, null, null, null, null).items().get(0).remainingMeals());

        Map<String, Object> disabled = menuScheduleService.disable(1L);
        assertEquals("DISABLED", disabled.get("status"));
    }
}
