package com.jzqs.app.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jzqs.app.customer.api.RemarkSuggestionResponse;
import com.jzqs.app.customer.service.CustomerAssetService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class RemarkSuggestionServiceTest {
    private static final long CUSTOMER_ID = 9921L;
    private static final long WALLET_ID = 9921L;
    private static final long DAILY_ORDER_ID = 9921L;
    private static final long MEAL_SLOT_ORDER_ID = 9921L;


    @Autowired
    private CustomerAssetService customerAssetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        jdbcTemplate.update("DELETE FROM subscription_rules WHERE id = 21");
        jdbcTemplate.update("DELETE FROM delivery_receipts WHERE id = ?", MEAL_SLOT_ORDER_ID);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id = ?", MEAL_SLOT_ORDER_ID);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id = ?", DAILY_ORDER_ID);
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE id IN (?, ?)", 99211L, 99212L);
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE id = ?", WALLET_ID);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM cost_entries");
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active) VALUES (?, '备注建议客户9921', '13900009921', 'BACKEND', TRUE)",
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES (?, ?, 10, 0, 0, TRUE)",
            WALLET_ID,
            CUSTOMER_ID
        );

        jdbcTemplate.update(
            "INSERT INTO wallet_transactions (id, wallet_id, transaction_type, meal_delta, operator_name, remark, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
            99211L, WALLET_ID, "GRANT", 10, "后台客服", "测试续卡赠送9921"
        );
        jdbcTemplate.update(
            "INSERT INTO wallet_transactions (id, wallet_id, transaction_type, meal_delta, operator_name, remark, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
            99212L, WALLET_ID, "GRANT", 5, "后台客服", "测试补餐9921"
        );
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
            DAILY_ORDER_ID, CUSTOMER_ID, tomorrow, "BACKEND", "PENDING_DISPATCH", false
        );
        jdbcTemplate.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            MEAL_SLOT_ORDER_ID, DAILY_ORDER_ID, "LUNCH", "LUNCH", 1, 1L, "少饭9921", "不要辣9921", "PENDING_DISPATCH", "BACKEND"
        );
        jdbcTemplate.update(
            "INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, receipt_note, delivered_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
            MEAL_SLOT_ORDER_ID, MEAL_SLOT_ORDER_ID, "https://cos.example.com/r2.jpg", "已放前台"
        );
        jdbcTemplate.update(
            "INSERT INTO subscription_rules (id, customer_id, active, lunch_enabled, lunch_quantity, dinner_enabled, dinner_quantity, start_date, end_date, merchant_remark, is_priority_follow, paused) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            21L, CUSTOMER_ID, true, true, 1, false, 0, tomorrow, tomorrow.plusDays(30), "订阅备注9921", false, false
        );
        jdbcTemplate.update(
            "INSERT INTO cost_entries (id, cost_date, cost_category, amount, remark, recorded_by, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
            21L, tomorrow, "OTHER", 12.50, "买打包袋", "测试"
        );
    }

    @Test
    void shouldReturnWalletRemarkSuggestionsInRecentDistinctOrder() {
        RemarkSuggestionResponse response = customerAssetService.remarkSuggestions("WALLET_REMARK", null);

        assertEquals("WALLET_REMARK", response.scene());
        assertTrue(response.items().size() >= 2);
        assertEquals("测试补餐9921", response.items().get(0));
        assertEquals("测试续卡赠送9921", response.items().get(1));
    }

    @Test
    void shouldMergeOrderRelatedRemarkSuggestions() {
        RemarkSuggestionResponse response = customerAssetService.remarkSuggestions("ORDER_REMARK", CUSTOMER_ID);

        assertEquals("ORDER_REMARK", response.scene());
        assertEquals("少饭9921", response.items().get(0));
        assertEquals("不要辣9921", response.items().get(1));
        assertEquals("订阅备注9921", response.items().get(2));
    }
}
