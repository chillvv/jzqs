package com.jzqs.app.order.persistence;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderSubscriptionRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderSubscriptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int confirmSubscription(long confirmationId, String confirmedBy) {
        return jdbcTemplate.update(
            "UPDATE subscription_confirmations SET status = 'CONFIRMED', confirmed_by = ?, confirmed_at = CURRENT_TIMESTAMP WHERE id = ?",
            confirmedBy,
            confirmationId
        );
    }

    public int cancelSubscription(long confirmationId, String cancelReason) {
        return jdbcTemplate.update(
            "UPDATE subscription_confirmations SET status = 'CANCELLED', cancel_reason = ? WHERE id = ?",
            cancelReason,
            confirmationId
        );
    }

    public Integer findRemainingMeals(long customerId) {
        List<Integer> rows = jdbcTemplate.query(
            """
                SELECT COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0)
                FROM meal_wallets mw
                WHERE mw.customer_id = ?
                  AND mw.active = TRUE
                  AND (mw.expired_at IS NULL OR mw.expired_at >= CURRENT_TIMESTAMP)
                ORDER BY mw.id DESC
                LIMIT 1
                """,
            (rs, rowNum) -> rs.getInt(1),
            customerId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String findCustomerName(long customerId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT name FROM customers WHERE id = ?",
            (rs, rowNum) -> rs.getString("name"),
            customerId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String findAddressLine(long addressId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT address_line FROM customer_addresses WHERE id = ?",
            (rs, rowNum) -> rs.getString("address_line"),
            addressId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }
}
