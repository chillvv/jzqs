package com.jzqs.app.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Autowired
    private CustomerAssetService customerAssetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        jdbcTemplate.update("DELETE FROM delivery_receipts WHERE id > 1");
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id > 3");
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id > 3");
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE id > 3");
        jdbcTemplate.update("DELETE FROM cost_entries");

        jdbcTemplate.update(
            "UPDATE customers SET remark = ?, priority_note = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1",
            "不吃洋葱",
            "前台统一签收"
        );
        jdbcTemplate.update(
            "INSERT INTO wallet_transactions (id, wallet_id, transaction_type, meal_delta, operator_name, remark, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
            21L, 1L, "GRANT", 10, "后台客服", "续卡赠送"
        );
        jdbcTemplate.update(
            "INSERT INTO wallet_transactions (id, wallet_id, transaction_type, meal_delta, operator_name, remark, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
            22L, 1L, "GRANT", 5, "后台客服", "补餐"
        );
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
            21L, 1L, tomorrow, "BACKEND", "PENDING_DISPATCH", false
        );
        jdbcTemplate.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, user_note, status, source_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            21L, 21L, "LUNCH", 1, 1L, "少饭", "不要辣", "PENDING_DISPATCH", "BACKEND"
        );
        jdbcTemplate.update(
            "INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, receipt_note, delivered_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
            21L, 21L, "https://cos.example.com/r2.jpg", "已放前台"
        );
        jdbcTemplate.update(
            "INSERT INTO subscription_rules (id, customer_id, active, lunch_enabled, lunch_quantity, dinner_enabled, dinner_quantity, start_date, end_date, default_note, is_priority_follow, paused) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            21L, 1L, true, true, 1, false, 0, tomorrow, tomorrow.plusDays(30), "少饭", false, false
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
        assertEquals(2, response.items().size());
        assertEquals("补餐", response.items().get(0));
        assertEquals("续卡赠送", response.items().get(1));
    }

    @Test
    void shouldMergeOrderRelatedRemarkSuggestions() {
        RemarkSuggestionResponse response = customerAssetService.remarkSuggestions("ORDER_REMARK", null);

        assertEquals("ORDER_REMARK", response.scene());
        assertEquals("少饭", response.items().get(0));
        assertEquals("不要辣", response.items().get(1));
    }
}
