package com.jzqs.app.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jzqs.app.customer.service.CustomerAssetService;
import com.jzqs.app.dashboard.service.DashboardService;
import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.menu.service.MenuScheduleService;
import com.jzqs.app.order.service.OrderPrepService;
import com.jzqs.app.settings.service.SettingsService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AdminPersistenceServiceTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private OrderPrepService orderPrepService;

    @Autowired
    private MenuScheduleService menuScheduleService;

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private CustomerAssetService customerAssetService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSeedData() {
        LocalDate serveDate = LocalDate.now().plusDays(1);
        LocalDate weekStart = serveDate.minusDays(serveDate.getDayOfWeek().getValue() - 1L);
        LocalDate weekEnd = weekStart.plusDays(6);
        jdbcTemplate.update("DELETE FROM aftersale_actions");
        jdbcTemplate.update("DELETE FROM aftersale_cases");
        jdbcTemplate.update("DELETE FROM delivery_receipts");
        jdbcTemplate.update("DELETE FROM dispatch_assignments");
        jdbcTemplate.update("DELETE FROM meal_slot_orders");
        jdbcTemplate.update("DELETE FROM daily_orders");
        jdbcTemplate.update("DELETE FROM customer_addresses");
        jdbcTemplate.update("DELETE FROM customers");
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE id > 3");
        jdbcTemplate.update("DELETE FROM menu_week_items");
        jdbcTemplate.update("DELETE FROM menu_weeks");
        jdbcTemplate.update("INSERT INTO customers (id, name, phone, customer_status, source, active) VALUES (1, '张先生', '13800000001', 'FORMAL', 'MINIAPP', TRUE)");
        jdbcTemplate.update("INSERT INTO customers (id, name, phone, customer_status, source, active) VALUES (2, '李女士', '13900000002', 'FORMAL', 'MINIAPP', TRUE)");
        jdbcTemplate.update("INSERT INTO customers (id, name, phone, customer_status, source, active) VALUES (3, '王总', '13700000003', 'FORMAL', 'BACKEND', TRUE)");
        jdbcTemplate.update("INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (1, 1, '张先生', '13800000001', '高新区科技园A座8层', '高新区', TRUE)");
        jdbcTemplate.update("INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (2, 2, '李女士', '13900000002', '阳光小区3栋2单元', '老城区', TRUE)");
        jdbcTemplate.update("INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (3, 3, '王总', '13700000003', '财富中心写字楼1201', '商务区', TRUE)");
        jdbcTemplate.update(
            "INSERT INTO menu_weeks (id, week_start_date, week_end_date, status, published_at, created_by, published_by) VALUES (1, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP, 'test', 'test')",
            weekStart,
            weekEnd
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (1, 1, ?, ?, 'LUNCH', 'ACTIVE', '[\"香煎鸡胸肉\",\"清炒时蔬\"]', 420, '香煎鸡胸肉+清炒时蔬', '香煎鸡胸肉+清炒时蔬', 420, '少油少盐', '/assets/meal-default.jpeg', 1)",
            serveDate,
            serveDate.getDayOfWeek().getValue()
        );
        jdbcTemplate.update(
            "INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order) VALUES (2, 1, ?, ?, 'DINNER', 'ACTIVE', '[\"清蒸海鲈鱼\",\"炒时蔬\"]', 350, '清蒸海鲈鱼+炒时蔬', '清蒸海鲈鱼+炒时蔬', 350, '-', '/assets/meal-default.jpeg', 2)",
            serveDate,
            serveDate.getDayOfWeek().getValue()
        );
        jdbcTemplate.update(
            "UPDATE admin_settings SET ordering_enabled = TRUE, holiday_notice_title = '节假日/店休特殊公告', holiday_notice_desc = '在小程序首页顶部展示的提示信息' WHERE id = 1"
        );
        jdbcTemplate.update("UPDATE meal_wallets SET total_meals = CASE id WHEN 1 THEN 33 WHEN 2 THEN 7 WHEN 3 THEN 33 END, reserved_meals = CASE id WHEN 1 THEN 0 ELSE 1 END, consumed_meals = CASE id WHEN 2 THEN 5 ELSE 20 END WHERE id IN (1, 2, 3)");
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (1, 1, ?, 'MINIAPP', 'DELIVERED', FALSE, CURRENT_TIMESTAMP)", serveDate);
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (2, 2, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)", serveDate);
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (3, 3, ?, 'BACKEND', 'DISPATCHING', FALSE, CURRENT_TIMESTAMP)", serveDate);
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (1, 1, 'LUNCH', 'LUNCH', 1, 1, '少饭，不要洋葱', 'DELIVERED', 'MINIAPP')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (2, 2, 'DINNER', 'DINNER', 1, 2, '-', 'PENDING_DISPATCH', 'MINIAPP')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (3, 3, 'LUNCH', 'LUNCH', 1, 3, '微辣', 'DISPATCHING', 'BACKEND')");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (1, 1, '骑手老周', '高新区', 'DELIVERED')");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (2, 3, '骑手小李', '商务区', 'DISPATCHING')");
        jdbcTemplate.update("INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, receipt_note, delivered_at) VALUES (1, 1, 'https://cos.example.com/receipt-1.jpg', '已放前台', CURRENT_TIMESTAMP)");
    }

    @Test
    void shouldReadDashboardOrdersCustomersMenuDispatchAndSettingsFromDatabase() {
        assertEquals(1, dashboardService.overview().deliveredToday());
        assertEquals(3, dashboardService.overview().tomorrowMealCount());
        assertEquals(3, orderPrepService.prepPage(null).items().size());
        assertEquals("张先生", orderPrepService.prepPage(null).items().get(0).customerName());
        assertEquals(2, menuScheduleService.list().items().size());
        assertEquals(2, dispatchService.board().items().size());
        assertEquals(3, customerAssetService.listAssets(null, null, null, null, null).items().size());

        assertTrue(settingsService.operationSettings().orderingEnabled());
        assertEquals("节假日/店休特殊公告", settingsService.operationSettings().holidayNoticeTitle());
        assertFalse(dispatchService.board().items().get(0).canNotifyCustomer());
    }
}
