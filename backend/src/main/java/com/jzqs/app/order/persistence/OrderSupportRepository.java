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

    public List<Long> findCustomerAddressIds(long customerId, String deliveryAddress) {
        return jdbcTemplate.queryForList(
            "SELECT id FROM customer_addresses WHERE customer_id = ? AND address_line = ?",
            Long.class,
            customerId,
            deliveryAddress
        );
    }

    public CustomerProfile findCustomerProfile(long customerId) {
        return jdbcTemplate.query(
            "SELECT name, phone FROM customers WHERE id = ?",
            ps -> ps.setLong(1, customerId),
            rs -> rs.next() ? new CustomerProfile(rs.getString("name"), rs.getString("phone")) : null
        );
    }

    public long insertCustomerAddress(long customerId, String contactName, String contactPhone, String deliveryAddress, String areaCode) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                "INSERT INTO customer_addresses (customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (?, ?, ?, ?, ?, FALSE)",
                new String[] {"id"}
            );
            ps.setLong(1, customerId);
            ps.setString(2, contactName);
            ps.setString(3, contactPhone);
            ps.setString(4, deliveryAddress);
            ps.setString(5, areaCode);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public List<String> findAddressLines(long addressId, long customerId) {
        return jdbcTemplate.queryForList(
            "SELECT address_line FROM customer_addresses WHERE id = ? AND customer_id = ?",
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

    /** 查找该钱包下该订单对应的原扣餐流水（与售后退款链路同一口径：优先关联本订单，老数据未关联订单的兜底）。 */
    public Long findOriginalConsumeTransactionId(long walletId, long orderId) {
        return jdbcTemplate.query(
            """
                SELECT id
                FROM wallet_transactions
                WHERE wallet_id = ?
                  AND transaction_type = 'CONSUME'
                  AND refunded = FALSE
                  AND (related_order_id = ? OR related_order_id IS NULL)
                ORDER BY CASE WHEN related_order_id = ? THEN 0 ELSE 1 END, id DESC
                LIMIT 1
                """,
            ps -> {
                ps.setLong(1, walletId);
                ps.setLong(2, orderId);
                ps.setLong(3, orderId);
            },
            rs -> rs.next() ? rs.getLong("id") : null
        );
    }

    /** 将原扣餐流水标记为已退款，并回填关联的退款流水与原因，保证用户端流水状态闭环。 */
    public void markTransactionRefunded(long transactionId, long relatedTransactionId, String reasonCode, String reasonText) {
        jdbcTemplate.update(
            """
                UPDATE wallet_transactions
                SET refunded = TRUE,
                    related_transaction_id = ?,
                    refund_reason_code = ?,
                    refund_reason_text = ?
                WHERE id = ?
                """,
            relatedTransactionId,
            reasonCode,
            reasonText,
            transactionId
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

    public record CustomerProfile(String name, String phone) {
    }
}
