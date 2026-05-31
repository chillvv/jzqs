package com.jzqs.app.aftersale.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jzqs.app.aftersale.api.AdminAftersaleResolveRequest;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
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
        jdbcTemplate.update("DELETE FROM aftersale_actions");
        jdbcTemplate.update("DELETE FROM aftersale_cases");
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE id > 3");
        jdbcTemplate.update(
            "UPDATE meal_wallets SET total_meals = CASE id WHEN 1 THEN 33 WHEN 2 THEN 7 WHEN 3 THEN 33 END, " +
                "reserved_meals = CASE id WHEN 1 THEN 1 WHEN 2 THEN 1 ELSE 1 END, " +
                "consumed_meals = CASE id WHEN 1 THEN 20 WHEN 2 THEN 5 ELSE 20 END"
        );
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = CASE id WHEN 1 THEN 'DELIVERED' WHEN 2 THEN 'PENDING_DISPATCH' ELSE 'DISPATCHING' END");
        jdbcTemplate.update("DELETE FROM dispatch_batch_items");
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE id > 2");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (3, 2, '骑手小王', '老城区', 'DISPATCHING')");
    }

    @Test
    void shouldResolveRefundAndRollbackWalletOnce() {
        long originalTransactionId = insertOriginalWalletTransaction(2L, 2L, "RESERVE", -1, "用户自主下单占用餐次");
        long caseId = insertPendingRefundCase();

        Map<String, Object> result = aftersaleService.resolveCase(12L, new AdminAftersaleResolveRequest(
            "REFUND_TO_WALLET",
            true,
            1,
            "同意退款，退回餐次",
            "后台客服"
        ));

        assertThat(result.get("status")).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM meal_slot_orders WHERE id = ?",
            String.class,
            2L
        )).isEqualTo("REFUNDED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT reserved_meals FROM meal_wallets WHERE id = ?",
            Integer.class,
            2L
        )).isEqualTo(0);
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
        assertThat(jdbcTemplate.queryForObject(
            "SELECT related_transaction_id FROM wallet_transactions WHERE id = ?",
            Long.class,
            refundTransactionId
        )).isEqualTo(originalTransactionId);
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
        long originalTransactionId = insertOriginalWalletTransaction(2L, 2L, "RESERVE", -1, "用户自主下单占用餐次");
        long caseId = insertPendingRefundCase();

        Map<String, Object> result = aftersaleService.resolveCase(12L, new AdminAftersaleResolveRequest(
            "REFUND_TO_WALLET",
            true,
            3,
            "同意退款，退回原扣餐次",
            "后台客服"
        ));

        assertThat(result.get("status")).isEqualTo("COMPLETED");
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
        assertThat(jdbcTemplate.queryForObject(
            "SELECT related_transaction_id FROM wallet_transactions WHERE related_aftersale_id = ? AND transaction_type = ?",
            Long.class,
            caseId,
            "REFUND_RETURN"
        )).isEqualTo(originalTransactionId);
    }

    @Test
    void shouldAutoRefundUndeliveredOrderAndTraceReserveTransaction() {
        long originalTransactionId = insertOriginalWalletTransaction(2L, 2L, "RESERVE", -1, "用户自主下单占用餐次");

        Map<String, Object> result = aftersaleService.createMobileCase(
            2L,
            2L,
            new MobileCreateAfterSaleRequest("REFUND", "USER_TEMP_CHANGE", "临时有事", "希望退回餐次")
        );

        assertThat(result.get("status")).isEqualTo("COMPLETED");
        long caseId = ((Number) result.get("afterSaleId")).longValue();
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
        assertThat(jdbcTemplate.queryForObject(
            "SELECT reserved_meals FROM meal_wallets WHERE id = ?",
            Integer.class,
            2L
        )).isEqualTo(0);
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
        assertThat(jdbcTemplate.queryForObject(
            "SELECT related_transaction_id FROM wallet_transactions WHERE id = ?",
            Long.class,
            refundTransactionId
        )).isEqualTo(originalTransactionId);
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

        Map<String, Object> result = aftersaleService.createMobileCase(
            1L,
            1L,
            new MobileCreateAfterSaleRequest("REFUND", "DELIVERY_ISSUE", "已送达但餐品异常", "需要人工处理")
        );

        assertThat(result.get("status")).isEqualTo("PENDING");
        long caseId = ((Number) result.get("afterSaleId")).longValue();
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
