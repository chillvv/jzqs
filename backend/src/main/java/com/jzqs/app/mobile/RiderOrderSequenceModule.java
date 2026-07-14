package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.mobile.api.RiderOrderSequenceSaveResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RiderOrderSequenceModule {
    private final JdbcTemplate jdbcTemplate;

    RiderOrderSequenceModule(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    RiderOrderSequenceSaveResponse saveOrderSequence(
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

        List<Long> existingBatchItemIds = jdbcTemplate.query(
            """
                SELECT id
                FROM dispatch_batch_items
                WHERE batch_id = ?
                  AND id IN (%s)
                ORDER BY current_sequence ASC, id ASC
                """.formatted("?,".repeat(batchItemIds.size()).replaceAll(",$", "")),
            ps -> {
                ps.setLong(1, batchId);
                for (int i = 0; i < batchItemIds.size(); i++) {
                    ps.setLong(i + 2, batchItemIds.get(i));
                }
            },
            (rs, rowNum) -> rs.getLong("id")
        );
        if (existingBatchItemIds.size() != batchItemIds.size()) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "排序列表包含无效的批次项"
            );
        }

        List<Long> fullBatchItemIds = jdbcTemplate.query(
            """
                SELECT id
                FROM dispatch_batch_items
                WHERE batch_id = ?
                ORDER BY current_sequence ASC, id ASC
                """,
            (rs, rowNum) -> rs.getLong("id"),
            batchId
        );
        List<Long> mergedBatchItemIds = new ArrayList<>();
        int reorderedIndex = 0;
        for (Long existingBatchItemId : fullBatchItemIds) {
            if (existingBatchItemIds.contains(existingBatchItemId)) {
                mergedBatchItemIds.add(batchItemIds.get(reorderedIndex++));
            } else {
                mergedBatchItemIds.add(existingBatchItemId);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
            "UPDATE dispatch_batch_items SET current_sequence = current_sequence + 1000 WHERE batch_id = ?",
            batchId
        );
        for (int i = 0; i < mergedBatchItemIds.size(); i++) {
            Long batchItemId = mergedBatchItemIds.get(i);
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
                throw new BusinessException(
                    ErrorCode.RIDER_TASK_NOT_FOUND,
                    "更新配送顺序失败"
                );
            }
        }

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
