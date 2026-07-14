package com.jzqs.app.order.persistence;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrderOperationRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderOperationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int updateMerchantRemark(long orderId, String merchantRemark) {
        return jdbcTemplate.update(
            "UPDATE meal_slot_orders SET merchant_remark = ? WHERE id = ?",
            merchantRemark,
            orderId
        );
    }

    public OrderProfileRecord findOrderProfile(long orderId) {
        return jdbcTemplate.query(
            """
                SELECT
                    do.customer_id,
                    mso.meal_period,
                    mso.quantity,
                    mso.status,
                    COALESCE(ca.address_line, '') AS delivery_address,
                    COALESCE(mso.merchant_remark, '') AS merchant_remark,
                    COALESCE(mso.is_priority, FALSE) AS is_priority
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                LEFT JOIN customer_addresses ca ON ca.id = mso.address_id
                WHERE mso.id = ?
                """,
            ps -> ps.setLong(1, orderId),
            rs -> rs.next()
                ? new OrderProfileRecord(
                    rs.getLong("customer_id"),
                    rs.getString("meal_period"),
                    rs.getInt("quantity"),
                    rs.getString("delivery_address"),
                    rs.getString("merchant_remark"),
                    rs.getBoolean("is_priority"),
                    rs.getString("status")
                )
                : null
        );
    }

    public int updateOrderProfile(long orderId, String mealPeriod, int quantity, long addressId, String merchantRemark, boolean isPriority, String status) {
        return jdbcTemplate.update(
            "UPDATE meal_slot_orders SET meal_period = ?, quantity = ?, address_id = ?, merchant_remark = ?, is_priority = ?, status = ? WHERE id = ?",
            mealPeriod,
            quantity,
            addressId,
            merchantRemark,
            isPriority,
            status,
            orderId
        );
    }

    public List<Long> findOrderCustomerIds(long orderId) {
        return jdbcTemplate.query(
            """
                SELECT do.customer_id
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE mso.id = ?
                """,
            (rs, rowNum) -> rs.getLong("customer_id"),
            orderId
        );
    }

    public void insertOrderNote(long orderId, long customerId, String noteType, String scopeType, String content, String createdBy) {
        jdbcTemplate.update(
            """
                INSERT INTO order_notes (
                    meal_slot_order_id, customer_id, note_type, source_type, scope_type, content, effective_status, created_by
                ) VALUES (?, ?, ?, 'MERCHANT_ORDER_ONCE', ?, ?, 'ACTIVE', ?)
                """,
            orderId,
            customerId,
            noteType,
            scopeType,
            content,
            createdBy
        );
    }

    public int cancelOrder(long orderId) {
        return jdbcTemplate.update(
            "UPDATE meal_slot_orders SET status = 'CANCELLED' WHERE id = ? AND status <> 'CANCELLED'",
            orderId
        );
    }

    public List<Long> findDispatchBatchIds(long orderId) {
        return jdbcTemplate.query(
            "SELECT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
            (rs, rowNum) -> rs.getLong("batch_id"),
            orderId
        );
    }

    public void deleteDispatchBatchItems(long orderId) {
        jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE meal_slot_order_id = ?", orderId);
    }

    public void refreshDispatchBatchMetrics(long batchId) {
        jdbcTemplate.update(
            """
                UPDATE dispatch_batches
                SET total_count = (SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ?),
                    delivered_count = (SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ? AND item_status = 'DELIVERED')
                WHERE id = ?
                """,
            batchId,
            batchId,
            batchId
        );
    }

    public void deleteDispatchAssignments(long orderId) {
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE meal_slot_order_id = ?", orderId);
    }

    public WalletReserveContext findWalletReserveContext(long orderId) {
        return jdbcTemplate.query(
            """
                SELECT mso.quantity, do.customer_id
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE mso.id = ?
                """,
            ps -> ps.setLong(1, orderId),
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                long customerIdValue = rs.getLong("customer_id");
                return new WalletReserveContext(
                    rs.wasNull() ? null : customerIdValue,
                    rs.getInt("quantity")
                );
            }
        );
    }

    public void releaseReservedMeals(long walletId, int quantity) {
        jdbcTemplate.update(
            "UPDATE meal_wallets SET reserved_meals = CASE WHEN reserved_meals >= ? THEN reserved_meals - ? ELSE 0 END WHERE id = ?",
            quantity,
            quantity,
            walletId
        );
    }

    public Integer countOrderById(long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE id = ?",
            Integer.class,
            orderId
        );
    }

    public DeleteOrderContext findDeleteOrderContext(long orderId) {
        return jdbcTemplate.query(
            """
                SELECT mso.id, mso.daily_order_id, mso.status, mso.quantity, do.customer_id
                FROM meal_slot_orders mso
                LEFT JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE mso.id = ?
                """,
            ps -> ps.setLong(1, orderId),
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                long customerIdValue = rs.getLong("customer_id");
                return new DeleteOrderContext(
                    rs.getLong("daily_order_id"),
                    rs.wasNull() ? null : customerIdValue,
                    rs.getString("status"),
                    rs.getInt("quantity")
                );
            }
        );
    }

    public void deleteDeliveryReceipts(long orderId) {
        jdbcTemplate.update("DELETE FROM delivery_receipts WHERE meal_slot_order_id = ?", orderId);
    }

    public void deleteCustomerDeliverySubscriptions(long orderId) {
        jdbcTemplate.update("DELETE FROM customer_delivery_subscriptions WHERE meal_slot_order_id = ?", orderId);
    }

    public void deleteOrderNotes(long orderId) {
        jdbcTemplate.update("DELETE FROM order_notes WHERE meal_slot_order_id = ?", orderId);
    }

    public void deleteMealSlotOrder(long orderId) {
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id = ?", orderId);
    }

    public Integer countRemainingSlots(long dailyOrderId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE daily_order_id = ?",
            Integer.class,
            dailyOrderId
        );
    }

    public void deleteDailyOrder(long dailyOrderId) {
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id = ?", dailyOrderId);
    }

    public Integer countDeliveredOrder(long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE id = ? AND status = 'DELIVERED'",
            Integer.class,
            orderId
        );
    }

    public Long findAvailableWalletId(long customerId) {
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
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    public Integer findRemainingMeals(long walletId) {
        return jdbcTemplate.query(
            "SELECT (total_meals - reserved_meals - consumed_meals) FROM meal_wallets WHERE id = ?",
            ps -> ps.setLong(1, walletId),
            rs -> rs.next() ? rs.getInt(1) : 0
        );
    }

    public Long findExistingDailyOrderId(long customerId, LocalDate serveDate) {
        return jdbcTemplate.query(
            """
                SELECT id
                FROM daily_orders
                WHERE customer_id = ? AND serve_date = ?
                ORDER BY id DESC
                LIMIT 1
                """,
            ps -> {
                ps.setLong(1, customerId);
                ps.setObject(2, serveDate);
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    public long insertDailyOrder(long customerId, LocalDate serveDate, String source) {
        return insertAndReturnId(
            "INSERT INTO daily_orders (customer_id, serve_date, source, status, locked, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            customerId,
            serveDate,
            source,
            "PENDING_DISPATCH",
            false,
            Timestamp.valueOf(LocalDateTime.now())
        );
    }

    public Long findMergeTargetOrderId(long customerId, LocalDate serveDate, String mealPeriod, String deliveryMealPeriod, long addressId) {
        return jdbcTemplate.query(
            """
                SELECT mso.id
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE do.customer_id = ?
                  AND do.serve_date = ?
                  AND mso.meal_period = ?
                  AND mso.delivery_meal_period = ?
                  AND mso.address_id = ?
                  AND mso.status NOT IN ('CANCELLED', 'REFUNDED')
                  AND NOT EXISTS (
                      SELECT 1
                      FROM aftersale_cases ac
                      WHERE ac.meal_slot_order_id = mso.id
                        AND ac.issue_type = 'REFUND'
                        AND ac.status IN ('PENDING', 'PROCESSING', 'APPROVED')
                  )
                ORDER BY mso.id DESC
                LIMIT 1
                """,
            ps -> {
                ps.setLong(1, customerId);
                ps.setObject(2, serveDate);
                ps.setString(3, mealPeriod);
                ps.setString(4, deliveryMealPeriod);
                ps.setLong(5, addressId);
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    public String findOrderMerchantRemark(long orderId) {
        String existingRemark = jdbcTemplate.query(
            "SELECT COALESCE(merchant_remark, '') AS merchant_remark FROM meal_slot_orders WHERE id = ?",
            ps -> ps.setLong(1, orderId),
            rs -> rs.next() ? rs.getString("merchant_remark") : ""
        );
        return existingRemark == null ? "" : existingRemark;
    }

    public void mergeOrderQuantityAndRemark(long orderId, int quantity, String merchantRemark) {
        jdbcTemplate.update(
            "UPDATE meal_slot_orders SET quantity = quantity + ?, merchant_remark = ? WHERE id = ?",
            quantity,
            merchantRemark,
            orderId
        );
    }

    public void increaseReservedMeals(long walletId, int quantity) {
        jdbcTemplate.update("UPDATE meal_wallets SET reserved_meals = reserved_meals + ? WHERE id = ?", quantity, walletId);
    }

    public long insertMealSlotOrder(long dailyOrderId, String mealPeriod, String deliveryMealPeriod, int quantity, long addressId, String merchantRemark, String source, boolean confirmedFromSubscription) {
        return insertAndReturnId(
            "INSERT INTO meal_slot_orders (daily_order_id, meal_period, delivery_meal_period, quantity, address_id, merchant_remark, status, source_type, confirmed_from_subscription) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            dailyOrderId,
            mealPeriod,
            deliveryMealPeriod,
            quantity,
            addressId,
            merchantRemark,
            "PENDING_DISPATCH",
            source,
            confirmedFromSubscription
        );
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public record OrderProfileRecord(
        long customerId,
        String mealPeriod,
        int quantity,
        String deliveryAddress,
        String merchantRemark,
        boolean priority,
        String status
    ) {
    }

    public record WalletReserveContext(Long customerId, int quantity) {
    }

    public record DeleteOrderContext(long dailyOrderId, Long customerId, String status, int quantity) {
    }
}
