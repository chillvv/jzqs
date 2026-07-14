package com.jzqs.app.order.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderDispatchRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderDispatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SpecialDispatchContext> findSpecialDispatchContext(long orderId) {
        List<SpecialDispatchContext> rows = jdbcTemplate.query(
            """
                SELECT
                    mso.meal_period,
                    COALESCE(mso.delivery_meal_period, mso.meal_period) AS delivery_meal_period,
                    mso.status
                FROM meal_slot_orders mso
                WHERE mso.id = ?
                """,
            (rs, rowNum) -> new SpecialDispatchContext(
                rs.getString("meal_period"),
                rs.getString("delivery_meal_period"),
                rs.getString("status")
            ),
            orderId
        );
        return rows.stream().findFirst();
    }

    public void resetDispatchFlow(long orderId) {
        List<Long> batchIds = jdbcTemplate.query(
            "SELECT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
            (rs, rowNum) -> rs.getLong("batch_id"),
            orderId
        );
        if (!batchIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE meal_slot_order_id = ?", orderId);
            for (Long batchId : batchIds) {
                refreshDispatchBatchMetrics(batchId);
            }
        }
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE meal_slot_order_id = ?", orderId);
    }

    public void updateSpecialDispatch(long orderId, String deliveryMealPeriod) {
        jdbcTemplate.update(
            "UPDATE meal_slot_orders SET delivery_meal_period = ?, status = 'PENDING_DISPATCH' WHERE id = ?",
            deliveryMealPeriod,
            orderId
        );
    }

    public void resetSpecialDispatch(long orderId) {
        jdbcTemplate.update(
            "UPDATE meal_slot_orders SET delivery_meal_period = meal_period, status = 'PENDING_DISPATCH' WHERE id = ?",
            orderId
        );
    }

    public Optional<String> findOrderStatus(long orderId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT status FROM meal_slot_orders WHERE id = ?",
            (rs, rowNum) -> rs.getString("status"),
            orderId
        );
        return rows.stream().findFirst();
    }

    public Optional<DeliveryReceiptRecord> findLatestDeliveryReceipt(long orderId) {
        List<DeliveryReceiptRecord> rows = jdbcTemplate.query(
            """
                SELECT id, receipt_url
                FROM delivery_receipts
                WHERE meal_slot_order_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
            (rs, rowNum) -> new DeliveryReceiptRecord(
                rs.getLong("id"),
                rs.getString("receipt_url")
            ),
            orderId
        );
        return rows.stream().findFirst();
    }

    public void clearLatestDeliveryReceipt(long receiptId) {
        jdbcTemplate.update(
            """
                UPDATE delivery_receipts
                SET receipt_url = '',
                    receipt_note = ''
                WHERE id = ?
                """,
            receiptId
        );
    }

    private void refreshDispatchBatchMetrics(long batchId) {
        jdbcTemplate.update(
            """
                UPDATE dispatch_batches
                SET total_count = (
                        SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ?
                    ),
                    delivered_count = (
                        SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ? AND item_status = 'DELIVERED'
                    )
                WHERE id = ?
                """,
            batchId,
            batchId,
            batchId
        );
    }

    public record SpecialDispatchContext(
        String mealPeriod,
        String deliveryMealPeriod,
        String status
    ) {
    }

    public record DeliveryReceiptRecord(long id, String receiptUrl) {
    }
}
