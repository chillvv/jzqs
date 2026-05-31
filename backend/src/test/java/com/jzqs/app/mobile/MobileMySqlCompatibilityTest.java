package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jzqs.app.customer.api.WalletTransactionResponse;
import com.jzqs.app.mobile.api.MobileMenuItemResponse;
import com.jzqs.app.mobile.api.MobileOrderItemResponse;
import com.jzqs.app.mobile.api.RiderAuthProfileResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MobileMySqlCompatibilityTest {

    @Autowired
    private MobilePortalService mobilePortalService;

    @Autowired
    private MobileAuthService mobileAuthService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetMobileFixtures() {
        jdbcTemplate.update("DELETE FROM menu_week_items WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM menu_weeks WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM delivery_receipts WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM package_plans WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id >= 951");
        jdbcTemplate.update("DELETE FROM customers WHERE id >= 901");

        jdbcTemplate.update(
            """
                INSERT INTO menu_weeks (
                    id, week_start_date, week_end_date, status, published_at, created_by, published_by
                ) VALUES (
                    901, DATE '2026-06-01', DATE '2026-06-07', 'PUBLISHED', CURRENT_TIMESTAMP, 'test', 'test'
                )
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO menu_week_items (
                    id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json,
                    total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order
                ) VALUES (
                    901, 901, DATE '2026-06-02', 2, 'LUNCH', 'ACTIVE', '["黑椒牛柳","米饭"]',
                    520, '黑椒牛柳饭', '黑椒牛柳+米饭', 520, '少油', '/assets/meal-default.jpeg', 1
                )
                """
        );
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source) VALUES (901, '兼容客户', '13800000901', 'MINIAPP')"
        );
        jdbcTemplate.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (901, 901, '兼容客户', '13800000901', '高新区测试地址', '高新区', TRUE)"
        );
        jdbcTemplate.update(
            "INSERT INTO package_plans (id, package_code, package_name, total_meals, enabled) VALUES (901, 'MYSQL_TEST', '兼容测试套餐', 30, TRUE)"
        );
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active) VALUES (901, 901, 901, 30, 0, 0, TRUE)"
        );
        jdbcTemplate.update(
            "INSERT INTO wallet_transactions (id, wallet_id, transaction_type, meal_delta, operator_name, remark, created_at) VALUES (901, 901, 'RESERVE', -1, '系统', '兼容测试', TIMESTAMP '2026-05-12 08:00:00')"
        );
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (901, 901, DATE '2026-06-02', 'MINIAPP', 'DELIVERED', FALSE, CURRENT_TIMESTAMP)"
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (
                    901, 901, 'LUNCH', 1, 901, '少饭', '少饭', 'DELIVERED', 'MINIAPP'
                )
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (
                    id, meal_slot_order_id, receipt_url, receipt_note, delivered_at, visible_at, visible_to_customer
                ) VALUES (
                    901, 901, 'https://img.example.com/receipt-901.jpg', '已放前台',
                    TIMESTAMP '2026-06-02 12:00:00', TIMESTAMP '2026-06-02 12:05:00', TRUE
                )
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, phone, wechat_open_id, employment_status, default_area_code, display_order,
                    current_openid, auth_status, display_name, first_login_at, last_login_at, assigned_by, created_at
                ) VALUES (
                    951, '兼容骑手', '13800000951', 'wx_compat_951', 'ACTIVE', '高新区', 1,
                    'openid_compat_951', 'ACTIVE', '兼容骑手展示名',
                    TIMESTAMP '2026-05-12 07:30:00', TIMESTAMP '2026-05-12 11:45:00', '老板', CURRENT_TIMESTAMP
                )
                """
        );
    }

    @Test
    void shouldReturnMobileDatesAndTimestampsAsStrings() {
        List<MobileMenuItemResponse> menus = mobilePortalService.publishedMenus("2026-06-02").items();
        List<MobileOrderItemResponse> orders = mobilePortalService.customerOrders(901L, null).items();
        List<WalletTransactionResponse> transactions = mobilePortalService.walletTransactions(901L).items();

        assertEquals("2026-06-02", menus.get(0).serveDate());
        assertEquals("2026-06-02", orders.get(0).serveDate());
        assertEquals("2026-06-02 12:00:00", orders.get(0).deliveredAt());
        assertEquals("2026-05-12 08:00:00", transactions.get(0).createdAt());
    }

    @Test
    void shouldReturnRiderProfileTimestampsAsStrings() {
        RiderAuthProfileResponse profile = mobileAuthService.riderProfile("兼容骑手");

        assertEquals("2026-05-12 07:30:00", profile.firstLoginAt());
        assertEquals("2026-05-12 11:45:00", profile.lastLoginAt());
    }
}
