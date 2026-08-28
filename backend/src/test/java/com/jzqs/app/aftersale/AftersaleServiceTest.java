package com.jzqs.app.aftersale;

import static org.assertj.core.api.Assertions.assertThat;

import com.jzqs.app.aftersale.api.AdminAftersaleCreateRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleCreateResponse;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveResponse;
import com.jzqs.app.aftersale.service.AftersaleService;
import java.time.LocalDate;
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
        LocalDate today = LocalDate.now();
        jdbcTemplate.update("DELETE FROM aftersale_actions");
        jdbcTemplate.update("DELETE FROM aftersale_cases");
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE id > 3");
        // 本类必须自建低 id 基础数据（V1 dump 从 382 开始无 id 1,2,3），
        // 不得依赖其他测试类的执行顺序副作用（曾导致单跑「订单不存在」而全量恰好通过）。
        jdbcTemplate.update("DELETE FROM dispatch_assignments");
        jdbcTemplate.update("DELETE FROM dispatch_batch_items");
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id IN (1, 2, 3)");
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id IN (1, 2, 3)");
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE id IN (1, 2, 3)");
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id IN (1, 2, 3)");
        jdbcTemplate.update("DELETE FROM customers WHERE id IN (1, 2, 3)");
        jdbcTemplate.update("INSERT INTO customers (id, name, phone, customer_status, source, active) VALUES (1, '张三', '13800000001', 'FORMAL', 'MINIAPP', TRUE)");
        jdbcTemplate.update("INSERT INTO customers (id, name, phone, customer_status, source, active) VALUES (2, '李四', '13800000002', 'FORMAL', 'MINIAPP', TRUE)");
        jdbcTemplate.update("INSERT INTO customers (id, name, phone, customer_status, source, active) VALUES (3, '王五', '13800000003', 'FORMAL', 'MINIAPP', TRUE)");
        jdbcTemplate.update("INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (1, 1, '张三', '13800000001', '高新区软件园T3', '高新区', TRUE)");
        jdbcTemplate.update("INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (2, 2, '李四', '13800000002', '地址2', '商务区', TRUE)");
        jdbcTemplate.update("INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (3, 3, '王五', '13800000003', '地址3', '商务区', TRUE)");
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (1, 1, ?, 'MINIAPP', 'DELIVERED', FALSE, CURRENT_TIMESTAMP)", today);
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (2, 2, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)", today.plusDays(1));
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (3, 3, ?, 'BACKEND', 'DISPATCHING', FALSE, CURRENT_TIMESTAMP)", today);
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (1, 1, 'LUNCH', 'LUNCH', 2, 1, '少饭', 'DELIVERED', 'MINIAPP')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (2, 2, 'DINNER', 'DINNER', 1, 2, '-', 'PENDING_DISPATCH', 'MINIAPP')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (3, 3, 'LUNCH', 'LUNCH', 1, 3, '微辣', 'DISPATCHING', 'BACKEND')");
        jdbcTemplate.update("INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES (1, 1, 33, 0, 20, TRUE)");
        jdbcTemplate.update("INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES (2, 2, 7, 1, 5, TRUE)");
        jdbcTemplate.update("INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES (3, 3, 33, 1, 20, TRUE)");
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = CASE id WHEN 1 THEN 'DELIVERED' WHEN 2 THEN 'PENDING_DISPATCH' ELSE 'DISPATCHING' END");
    }

    @Test
    void shouldCreateAndCompleteCompensationAftersale() {
        AdminAftersaleCreateResponse created = aftersaleService.createCase(new AdminAftersaleCreateRequest(
            1L,
            "DELIVERY_EXCEPTION",
            "SOUP_SPILL",
            "午餐撒漏",
            "补一份饮品",
            2,
            "NORMAL",
            "补偿一份饮品",
            "后台客服"
        ));
        long caseId = created.afterSaleId();

        AdminAftersaleResolveResponse resolved = aftersaleService.resolveCase(caseId, new AdminAftersaleResolveRequest(
            "COMPENSATE_ONLY",
            false,
            2,
            0,
            0,
            0,
            "补回 2 餐",
            "后台客服"
        ));

        assertThat(resolved.status()).isEqualTo("COMPLETED");

        Map<String, Object> aftersaleCase = jdbcTemplate.queryForMap(
            "SELECT status, wallet_delta, resolution_action FROM aftersale_cases WHERE id = ?",
            caseId
        );
        assertThat(aftersaleCase.get("status")).isEqualTo("COMPLETED");
        assertThat(aftersaleCase.get("wallet_delta")).isEqualTo(2);
        // 后台直接发起售后立即生效（createCase 即 COMPLETED），
        // 补偿动作统一为 COMPENSATE_MEALS（2810594 售后闭环），不再有 COMPENSATE_ONLY 中间态。
        assertThat(aftersaleCase.get("resolution_action")).isEqualTo("COMPENSATE_MEALS");

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
