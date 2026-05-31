package com.jzqs.app.aftersale;

import static org.assertj.core.api.Assertions.assertThat;

import com.jzqs.app.aftersale.api.AdminAftersaleCreateRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveRequest;
import com.jzqs.app.aftersale.service.AftersaleService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AftersaleServiceTest {

    @Autowired
    private AftersaleService aftersaleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM aftersale_actions");
        jdbcTemplate.update("DELETE FROM aftersale_cases");
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE id > 3");
        jdbcTemplate.update(
            "UPDATE meal_wallets SET total_meals = CASE id WHEN 1 THEN 33 WHEN 2 THEN 7 WHEN 3 THEN 33 END, " +
                "reserved_meals = CASE id WHEN 1 THEN 0 WHEN 2 THEN 1 ELSE 1 END, " +
                "consumed_meals = CASE id WHEN 1 THEN 20 WHEN 2 THEN 5 ELSE 20 END"
        );
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'DELIVERED' WHERE id = 1");
    }

    @Test
    void shouldCreateAndCompleteCompensationAftersale() {
        Map<String, Object> created = aftersaleService.createCase(new AdminAftersaleCreateRequest(
            1L,
            "DELIVERY_EXCEPTION",
            "SOUP_SPILL",
            "午餐撒漏",
            "补一份饮品",
            "后台客服"
        ));
        long caseId = ((Number) created.get("afterSaleId")).longValue();

        Map<String, Object> resolved = aftersaleService.resolveCase(caseId, new AdminAftersaleResolveRequest(
            "COMPENSATE_ONLY",
            false,
            2,
            "补回 2 餐",
            "后台客服"
        ));

        assertThat(resolved.get("status")).isEqualTo("COMPLETED");

        Map<String, Object> aftersaleCase = jdbcTemplate.queryForMap(
            "SELECT status, wallet_delta, resolution_action FROM aftersale_cases WHERE id = ?",
            caseId
        );
        assertThat(aftersaleCase.get("status")).isEqualTo("COMPLETED");
        assertThat(aftersaleCase.get("wallet_delta")).isEqualTo(2);
        assertThat(aftersaleCase.get("resolution_action")).isEqualTo("COMPENSATE_ONLY");

        Map<String, Object> wallet = jdbcTemplate.queryForMap(
            "SELECT total_meals, reserved_meals, consumed_meals FROM meal_wallets WHERE id = 1"
        );
        assertThat(wallet.get("total_meals")).isEqualTo(35);
        assertThat(wallet.get("reserved_meals")).isEqualTo(0);
        assertThat(wallet.get("consumed_meals")).isEqualTo(20);

        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(
            """
                SELECT transaction_type, meal_delta, related_aftersale_id
                FROM wallet_transactions
                WHERE wallet_id = 1
                ORDER BY id DESC
                LIMIT 1
                """
        );
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).get("transaction_type")).isEqualTo("COMPENSATION_RETURN");
        assertThat(transactions.get(0).get("meal_delta")).isEqualTo(2);
        assertThat(((Number) transactions.get(0).get("related_aftersale_id")).longValue()).isEqualTo(caseId);
    }
}
