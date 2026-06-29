package com.jzqs.app.order.persistence;

import java.sql.Timestamp;
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
        jdbcTemplate.update(
            "INSERT INTO wallet_transactions (wallet_id, transaction_type, meal_delta, operator_name, remark, created_at, related_order_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
            walletId,
            transactionType,
            mealDelta,
            operatorName,
            remark,
            Timestamp.valueOf(LocalDateTime.now()),
            relatedOrderId
        );
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
