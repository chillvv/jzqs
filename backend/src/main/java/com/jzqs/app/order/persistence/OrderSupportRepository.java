package com.jzqs.app.order.persistence;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrderSupportRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderSupportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long findActiveWalletIdByCustomerId(long customerId) {
        return jdbcTemplate.query(
            """
                SELECT id
                FROM meal_wallets
                WHERE customer_id = ?
                  AND active = TRUE
                  AND (expired_at IS NULL OR expired_at >= CURRENT_TIMESTAMP)
                ORDER BY id DESC
                LIMIT 1
                """,
            ps -> ps.setLong(1, customerId),
            rs -> rs.next() ? rs.getLong("id") : null
        );
    }

    public Long findDefaultAddressId(long customerId) {
        return jdbcTemplate.query(
            "SELECT id FROM customer_addresses WHERE customer_id = ? AND active = TRUE ORDER BY is_default DESC, id ASC LIMIT 1",
            ps -> ps.setLong(1, customerId),
            rs -> rs.next() ? rs.getLong("id") : null
        );
    }

    public List<String> findAddressLines(long addressId, long customerId) {
        return jdbcTemplate.queryForList(
            "SELECT address_line FROM customer_addresses WHERE id = ? AND customer_id = ? AND active = TRUE",
            String.class,
            addressId,
            customerId
        );
    }

    public void insertWalletTransaction(long walletId, String transactionType, int mealDelta, String operatorName, String remark, Long relatedOrderId) {
        insertWalletTransaction(walletId, transactionType, mealDelta, operatorName, null, remark, relatedOrderId);
    }

    public void insertWalletTransaction(long walletId, String transactionType, int mealDelta, String operatorName, Long operatorId, String remark, Long relatedOrderId) {
        jdbcTemplate.update(
            "INSERT INTO wallet_transactions (wallet_id, transaction_type, meal_delta, operator_name, operator_id, remark, created_at, related_order_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            walletId,
            transactionType,
            mealDelta,
            operatorName,
            operatorId,
            remark,
            Timestamp.valueOf(LocalDateTime.now()),
            relatedOrderId
        );
    }

    /** 插入流水并返回自增 id，用于建立「退款流水 -> 原扣餐流水」的 refunded 标记闭环。 */
    public long insertWalletTransactionReturnId(long walletId, String transactionType, int mealDelta, String operatorName, Long operatorId, String remark, Long relatedOrderId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                "INSERT INTO wallet_transactions (wallet_id, transaction_type, meal_delta, operator_name, operator_id, remark, created_at, related_order_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                new String[] {"id"}
            );
            ps.setLong(1, walletId);
            ps.setString(2, transactionType);
            ps.setInt(3, mealDelta);
            ps.setString(4, operatorName);
            if (operatorId == null) {
                ps.setNull(5, Types.BIGINT);
            } else {
                ps.setLong(5, operatorId);
            }
            ps.setString(6, remark);
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            if (relatedOrderId == null) {
                ps.setNull(8, Types.BIGINT);
            } else {
                ps.setLong(8, relatedOrderId);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    /** 将该订单所有未退款的原扣餐流水一次性标记为已退款（加餐合并订单可能有多笔 CONSUME 流水），
     *  并回填关联的退款流水与原因，保证用户端流水状态闭环。 */
    public void markOrderConsumeTransactionsRefunded(long walletId, long orderId, long relatedTransactionId, String reasonCode, String reasonText) {
        jdbcTemplate.update(
            """
                UPDATE wallet_transactions
                SET refunded = TRUE,
                    related_transaction_id = ?,
                    refund_reason_code = ?,
                    refund_reason_text = ?
                WHERE wallet_id = ?
                  AND transaction_type = 'CONSUME'
                  AND refunded = FALSE
                  AND related_order_id = ?
                """,
            relatedTransactionId,
            reasonCode,
            reasonText,
            walletId,
            orderId
        );
    }

    /** 删除订单时级联清理关联流水，避免用户端出现指向已删除订单的"孤儿流水"。 */
    public int deleteWalletTransactionsByOrderId(long orderId) {
        return jdbcTemplate.update("DELETE FROM wallet_transactions WHERE related_order_id = ?", orderId);
    }

    public List<String> findCustomerMerchantRemarks(long customerId) {
        return jdbcTemplate.queryForList(
            "SELECT COALESCE(merchant_remark, '') FROM customers WHERE id = ?",
            String.class,
            customerId
        );
    }
}
