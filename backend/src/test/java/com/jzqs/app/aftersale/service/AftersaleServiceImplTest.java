package com.jzqs.app.aftersale.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jzqs.app.aftersale.api.AdminAftersaleCreateResponse;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleResponse;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AftersaleServiceImplTest {

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
        // V1 种子中低 id（1,2,3）数据不存在（生产 dump 从 382/1097 开始），需自建基础数据
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
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (1, 1, 'LUNCH', 'LUNCH', 1, 1, '少饭', 'DELIVERED', 'MINIAPP')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (2, 2, 'DINNER', 'DINNER', 1, 2, '-', 'PENDING_DISPATCH', 'MINIAPP')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (3, 3, 'LUNCH', 'LUNCH', 1, 3, '微辣', 'DISPATCHING', 'BACKEND')");
        jdbcTemplate.update("INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES (1, 1, 33, 1, 20, TRUE)");
        jdbcTemplate.update("INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES (2, 2, 7, 1, 5, TRUE)");
        jdbcTemplate.update("INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES (3, 3, 33, 1, 20, TRUE)");
        jdbcTemplate.update(
            "UPDATE daily_orders SET serve_date = CASE id WHEN 1 THEN ? WHEN 2 THEN ? ELSE ? END WHERE id IN (1, 2, 3)",
            today,
            today.plusDays(1),
            today
        );
        jdbcTemplate.update(
            "UPDATE meal_wallets SET total_meals = CASE id WHEN 1 THEN 33 WHEN 2 THEN 7 WHEN 3 THEN 33 END, " +
                "reserved_meals = CASE id WHEN 1 THEN 1 WHEN 2 THEN 1 ELSE 1 END, " +
                "consumed_meals = CASE id WHEN 1 THEN 20 WHEN 2 THEN 5 ELSE 20 END " +
                "WHERE id IN (1, 2, 3)"
        );
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = CASE id WHEN 1 THEN 'DELIVERED' WHEN 2 THEN 'PENDING_DISPATCH' ELSE 'DISPATCHING' END");
        jdbcTemplate.update("DELETE FROM dispatch_batch_items");
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE id > 2");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (3, 2, '骑手小王', '老城区', 'DISPATCHING')");
    }

    @Test
    void shouldResolveRefundAndRollbackWalletOnce() {
        long originalTransactionId = insertOriginalWalletTransaction(2L, 2L, "CONSUME", -1, "用户自主下单扣减餐次");
        long caseId = insertPendingRefundCase();

        AdminAftersaleResolveResponse result = aftersaleService.resolveCase(12L, new AdminAftersaleResolveRequest(
            "REFUND_TO_WALLET",
            true,
            1,
            0,
            0,
            0,
            "同意退款，退回餐次",
            "后台客服"
        ));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM meal_slot_orders WHERE id = ?",
            String.class,
            2L
        )).isEqualTo("REFUNDED");
        // 远程实现：退款将 consumed_meals 加回（seed 为 5，退款 1 份 → 4），不再有预留层
        assertThat(jdbcTemplate.queryForObject(
            "SELECT consumed_meals FROM meal_wallets WHERE id = ?",
            Integer.class,
            2L
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wallet_transactions WHERE related_aftersale_id = ? AND transaction_type = ?",
            Integer.class,
            caseId,
            "REFUND_RETURN"
        )).isEqualTo(1);
        Long refundTransactionId = jdbcTemplate.queryForObject(
            "SELECT id FROM wallet_transactions WHERE related_aftersale_id = ? AND transaction_type = ?",
            Long.class,
            caseId,
            "REFUND_RETURN"
        );
        // 2810594 后 trace 方向：REFUND_RETURN.related_transaction_id 恒为 NULL，
        // 反向由 markOrderConsumeTransactionsRefunded 把原始 CONSUME 流水指向退款流水（下方 originalTrace 断言覆盖）。
        Map<String, Object> originalTrace = jdbcTemplate.queryForMap(
            """
                SELECT refunded, related_transaction_id, refund_reason_code, refund_reason_text
                FROM wallet_transactions
                WHERE id = ?
                """,
            originalTransactionId
        );
        assertThat(originalTrace.get("refunded")).isEqualTo(Boolean.TRUE);
        assertThat(((Number) originalTrace.get("related_transaction_id")).longValue()).isEqualTo(refundTransactionId);
        assertThat(originalTrace.get("refund_reason_code")).isEqualTo("USER_TEMP_CHANGE");
        assertThat(originalTrace.get("refund_reason_text")).isEqualTo("临时有事");
    }

    @Test
    void shouldIgnoreManualRefundQuantityAndUseOriginalDeduction() {
        long originalTransactionId = insertOriginalWalletTransaction(2L, 2L, "CONSUME", -1, "用户自主下单扣减餐次");
        long caseId = insertPendingRefundCase();

        AdminAftersaleResolveResponse result = aftersaleService.resolveCase(12L, new AdminAftersaleResolveRequest(
            "REFUND_TO_WALLET",
            true,
            3,
            0,
            0,
            0,
            "同意退款，退回原扣餐次",
            "后台客服"
        ));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT meal_delta FROM wallet_transactions WHERE related_aftersale_id = ? AND transaction_type = ?",
            Integer.class,
            caseId,
            "REFUND_RETURN"
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT wallet_delta FROM aftersale_cases WHERE id = ?",
            Integer.class,
            caseId
        )).isEqualTo(1);
        // 2810594 后 REFUND_RETURN.related_transaction_id 恒为 NULL（方向反向，见 shouldResolveRefundAndRollbackWalletOnce）
    }

    @Test
    void shouldAutoRefundUndeliveredOrderAndTraceReserveTransaction() {
        long originalTransactionId = insertOriginalWalletTransaction(2L, 2L, "CONSUME", -1, "用户自主下单扣减餐次");

        MobileCreateAfterSaleResponse result = aftersaleService.createMobileCase(
            2L,
            2L,
            new MobileCreateAfterSaleRequest("REFUND", "USER_TEMP_CHANGE", "临时有事", "希望退回餐次")
        );

        assertThat(result.status()).isEqualTo("COMPLETED");
        long caseId = result.afterSaleId();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM aftersale_cases WHERE id = ?",
            String.class,
            caseId
        )).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM meal_slot_orders WHERE id = ?",
            String.class,
            2L
        )).isEqualTo("REFUNDED");
        // 远程实现：退款将 consumed_meals 加回（seed 为 5，退款 1 份 → 4）
        assertThat(jdbcTemplate.queryForObject(
            "SELECT consumed_meals FROM meal_wallets WHERE id = ?",
            Integer.class,
            2L
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = ?",
            Integer.class,
            2L
        )).isEqualTo(0);
        Long refundTransactionId = jdbcTemplate.queryForObject(
            "SELECT id FROM wallet_transactions WHERE related_aftersale_id = ? AND transaction_type = ?",
            Long.class,
            caseId,
            "REFUND_RETURN"
        );
        // 2810594 后 trace 方向：REFUND_RETURN.related_transaction_id 恒为 NULL，
        // 反向由 markOrderConsumeTransactionsRefunded 把原始 CONSUME 流水指向退款流水（下方 originalTrace 断言覆盖）。
        Map<String, Object> originalTrace = jdbcTemplate.queryForMap(
            """
                SELECT refunded, related_transaction_id, refund_reason_code, refund_reason_text
                FROM wallet_transactions
                WHERE id = ?
                """,
            originalTransactionId
        );
        assertThat(originalTrace.get("refunded")).isEqualTo(Boolean.TRUE);
        assertThat(((Number) originalTrace.get("related_transaction_id")).longValue()).isEqualTo(refundTransactionId);
        assertThat(originalTrace.get("refund_reason_code")).isEqualTo("USER_TEMP_CHANGE");
        assertThat(originalTrace.get("refund_reason_text")).isEqualTo("临时有事");
    }

    @Test
    void shouldKeepDeliveredRefundForManualReview() {
        long originalTransactionId = insertOriginalWalletTransaction(1L, 1L, "CONSUME", -1, "送达后核销餐次");

        MobileCreateAfterSaleResponse result = aftersaleService.createMobileCase(
            1L,
            1L,
            new MobileCreateAfterSaleRequest("REFUND", "DELIVERY_ISSUE", "已送达但餐品异常", "需要人工处理")
        );

        assertThat(result.status()).isEqualTo("PENDING");
        long caseId = result.afterSaleId();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM aftersale_cases WHERE id = ?",
            String.class,
            caseId
        )).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM meal_slot_orders WHERE id = ?",
            String.class,
            1L
        )).isEqualTo("DELIVERED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT consumed_meals FROM meal_wallets WHERE id = ?",
            Integer.class,
            1L
        )).isEqualTo(20);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wallet_transactions WHERE related_aftersale_id = ?",
            Integer.class,
            caseId
        )).isEqualTo(0);
        Map<String, Object> originalTrace = jdbcTemplate.queryForMap(
            "SELECT refunded, related_transaction_id FROM wallet_transactions WHERE id = ?",
            originalTransactionId
        );
        assertThat(originalTrace.get("refunded")).isEqualTo(Boolean.FALSE);
        assertThat(originalTrace.get("related_transaction_id")).isNull();
    }

    private long insertPendingRefundCase() {
        jdbcTemplate.update(
            """
                INSERT INTO aftersale_cases (
                    id, meal_slot_order_id, customer_id, issue_type, issue_desc, resolution_type,
                    rollback_meal, bonus_meals, compensation_item, status, operator_name,
                    source, reason_code, user_remark, admin_remark, resolution_action,
                    wallet_delta, refund_blocking, requested_at, processed_at, processed_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            12L,
            2L,
            2L,
            "REFUND",
            "临时有事",
            "REGISTER_ONLY",
            false,
            0,
            "",
            "PENDING",
            "小程序用户",
            "USER_APPLY",
            "USER_TEMP_CHANGE",
            "希望退回餐次",
            "",
            null,
            0,
            true
        );
        return 12L;
    }

    private long insertOriginalWalletTransaction(long walletId, long orderId, String type, int mealDelta, String remark) {
        jdbcTemplate.update(
            """
                INSERT INTO wallet_transactions (
                    wallet_id, transaction_type, meal_delta, operator_name, remark,
                    related_order_id, related_aftersale_id, related_transaction_id,
                    snapshot_balance, refunded, refund_reason_code, refund_reason_text, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, FALSE, NULL, NULL, CURRENT_TIMESTAMP)
                """,
            walletId,
            type,
            mealDelta,
            "系统",
            remark,
            orderId,
            1
        );
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM wallet_transactions", Long.class);
    }
}
