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
        jdbcTemplate.update("INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, receipt_note, delivered_at) VALUES (1, 1, 'https://cos.example.com/receipt-1.jpg', '已放前台', TIMESTAMP '2026-05-10 12:00:00')");
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
