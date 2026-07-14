package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.mobile.api.RiderOrderStatusRevertResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RiderOrderStatusRevertModule {
    private final JdbcTemplate jdbcTemplate;

    RiderOrderStatusRevertModule(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    RiderOrderStatusRevertResponse revertOrderStatus(long mealSlotOrderId, String riderName) {
        Long batchItemId = jdbcTemplate.query(
            """
                SELECT dbi.id
                FROM dispatch_batch_items dbi
                JOIN dispatch_batches db ON db.id = dbi.batch_id
                JOIN rider_profiles rp ON rp.id = db.rider_profile_id
                WHERE dbi.meal_slot_order_id = ?
                  AND rp.rider_name = ?
                  AND dbi.item_status = 'DELIVERED'
                """,
            ps -> {
                ps.setLong(1, mealSlotOrderId);
                ps.setString(2, riderName);
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (batchItemId == null) {
            throw new BusinessException(
                ErrorCode.RIDER_TASK_NOT_FOUND,
                "未找到可撤回的订单，请确认订单状态"
            );
        }

        jdbcTemplate.update(
            "DELETE FROM delivery_receipts WHERE meal_slot_order_id = ?",
            mealSlotOrderId
        );

        jdbcTemplate.update(
            """
                UPDATE dispatch_batch_items
                SET item_status = 'PENDING',
                    reordered_at = ?
                WHERE id = ?
                """,
            Timestamp.valueOf(LocalDateTime.now()),
            batchItemId
        );

        jdbcTemplate.update(
            """
                UPDATE dispatch_batches db
                SET delivered_count = (
                    SELECT COALESCE(SUM(mso.quantity), 0)
                    FROM dispatch_batch_items dbi
                    JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
                    WHERE dbi.batch_id = db.id
                      AND dbi.item_status = 'DELIVERED'
                )
                WHERE id = (
                    SELECT batch_id
                    FROM dispatch_batch_items
                    WHERE id = ?
                )
                """,
            batchItemId
        );

        jdbcTemplate.update(
            "UPDATE meal_slot_orders SET status = 'DISPATCHING' WHERE id = ?",
            mealSlotOrderId
        );

        return new RiderOrderStatusRevertResponse(mealSlotOrderId, "PENDING");
    }
}
