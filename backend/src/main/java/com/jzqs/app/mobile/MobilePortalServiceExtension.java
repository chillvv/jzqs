package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.mobile.api.RiderOrderStatusRevertResponse;
import com.jzqs.app.mobile.api.RiderOrderSequenceSaveResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MobilePortalService 扩展方法
 * 包含撤回订单状态和保存订单排序功能
 * 
 * @author Kiro AI
 * @since 2026-05-22
 */
@Component
public class MobilePortalServiceExtension {
    
    private final JdbcTemplate jdbcTemplate;

    public MobilePortalServiceExtension(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 撤回订单状态
     * 将已完成的订单恢复为待配送状态
     * 
     * @param mealSlotOrderId 订单ID
     * @param riderName 骑手姓名
     * @return 操作结果
     */
    @Transactional
    public RiderOrderStatusRevertResponse revertOrderStatus(long mealSlotOrderId, String riderName) {
        // 1. 验证订单是否存在且属于该骑手
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

        // 2. 删除送达回执
        jdbcTemplate.update(
            "DELETE FROM delivery_receipts WHERE meal_slot_order_id = ?",
            mealSlotOrderId
        );

        // 3. 更新批次项状态为待配送
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

        // 4. 更新批次统计
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

        // 5. 更新订单状态
        jdbcTemplate.update(
            "UPDATE meal_slot_orders SET status = 'DISPATCHING' WHERE id = ?",
            mealSlotOrderId
        );

        return new RiderOrderStatusRevertResponse(mealSlotOrderId, "PENDING");
    }

    /**
     * 保存订单排序
     * 保存骑手自定义的配送顺序
     * 
     * @param riderName 骑手姓名
     * @param mealPeriod 餐期
     * @param batchItemIds 排序后的批次项ID列表
     * @return 操作结果
     */
    @Transactional
    public RiderOrderSequenceSaveResponse saveOrderSequence(
        String riderName,
        String mealPeriod,
        List<Long> batchItemIds
    ) {
        if (batchItemIds == null || batchItemIds.isEmpty()) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "排序列表不能为空"
            );
        }

        // 1. 验证批次是否存在
        Long batchId = jdbcTemplate.query(
            """
                SELECT db.id
                FROM dispatch_batches db
                JOIN rider_profiles rp ON rp.id = db.rider_profile_id
                WHERE rp.rider_name = ?
                  AND db.meal_period = ?
                  AND db.serve_date = CURRENT_DATE
                """,
            ps -> {
                ps.setString(1, riderName);
                ps.setString(2, mealPeriod);
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );

        if (batchId == null) {
            throw new BusinessException(
                ErrorCode.RIDER_TASK_NOT_FOUND,
                "未找到对应的配送批次"
            );
        }

        // 2. 更新每个批次项的序号
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < batchItemIds.size(); i++) {
            Long batchItemId = batchItemIds.get(i);
            int newSequence = i + 1;

            int updated = jdbcTemplate.update(
                """
                    UPDATE dispatch_batch_items
                    SET current_sequence = ?,
                        manually_adjusted = TRUE,
                        reordered_by = ?,
                        reordered_at = ?
                    WHERE id = ?
                      AND batch_id = ?
                      AND item_status != 'DELIVERED'
                    """,
                newSequence,
                riderName,
                Timestamp.valueOf(now),
                batchItemId,
                batchId
            );

            if (updated == 0) {
                // 如果更新失败，可能是已完成的订单，跳过
                continue;
            }
        }

        // 3. 更新批次的最后排序时间
        jdbcTemplate.update(
            """
                UPDATE dispatch_batches
                SET last_reordered_at = ?,
                    last_reordered_by = ?
                WHERE id = ?
                """,
            Timestamp.valueOf(now),
            riderName,
            batchId
        );

        return new RiderOrderSequenceSaveResponse(true, "订单排序已保存", batchId, batchItemIds.size());
    }
}
