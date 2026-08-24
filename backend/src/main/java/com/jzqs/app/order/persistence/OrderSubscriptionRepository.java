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

    /** 记录指定日期指定客户餐次被跳过（取消勾选），已存在则保持原记录 */
    public int insertSubscriptionImportSkip(java.sql.Date serveDate, long customerId, String mealPeriod, String skippedBy) {
        return jdbcTemplate.update(
            """
                INSERT IGNORE INTO subscription_import_skips (serve_date, customer_id, meal_period, skipped_by)
                VALUES (?, ?, ?, ?)
                """,
            serveDate,
            customerId,
            mealPeriod,
            skippedBy
        );
    }

    /** 删除指定日期指定客户餐次的跳过记录（恢复导入） */
    public int deleteSubscriptionImportSkip(java.sql.Date serveDate, long customerId, String mealPeriod) {
        return jdbcTemplate.update(
            """
                DELETE FROM subscription_import_skips
                WHERE serve_date = ? AND customer_id = ? AND meal_period = ?
                """,
            serveDate,
            customerId,
            mealPeriod
        );
    }

    /** 判断指定日期、指定餐期是否为店铺休息日（菜单排期 slot_status = REST） */
    public boolean isMealSlotRest(String serveDate, String mealPeriod) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM menu_week_items
                WHERE serve_date = ?
                  AND meal_period = ?
                  AND slot_status = 'REST'
                """,
            Integer.class,
            java.sql.Date.valueOf(serveDate),
            mealPeriod
        );
        return count != null && count > 0;
    }
}
