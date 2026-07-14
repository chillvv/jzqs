package com.jzqs.app.dispatch.service.impl;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
class DispatchBatchModule {
    private final JdbcTemplate jdbcTemplate;

    DispatchBatchModule(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    int ensureBatchItem(long orderId, long riderProfileId, String areaCode, LocalDate serveDate, String mealPeriod) {
        List<Long> batchIds = jdbcTemplate.query(
            "SELECT id FROM dispatch_batches WHERE serve_date = ? AND meal_period = ? AND rider_profile_id = ? AND area_code = ?",
            (rs, rowNum) -> rs.getLong("id"),
            serveDate,
            mealPeriod,
            riderProfileId,
            areaCode
        );
        long batchId;
        if (batchIds.isEmpty()) {
            batchId = insertAndReturnId(
                "INSERT INTO dispatch_batches (serve_date, meal_period, rider_profile_id, area_code, batch_status, total_count, delivered_count, current_sequence) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                serveDate,
                mealPeriod,
                riderProfileId,
                areaCode,
                "READY",
                0,
                0,
                1
            );
        } else {
            batchId = batchIds.get(0);
        }

        int finalSequence;
        List<Long> existingBatchIds = jdbcTemplate.query(
            "SELECT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
            (rs, rowNum) -> rs.getLong("batch_id"),
            orderId
        );
        if (existingBatchIds.isEmpty()) {
            int nextSequence = nextBatchSequence(batchId);
            jdbcTemplate.update(
                "INSERT IGNORE INTO dispatch_batch_items (batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted) VALUES (?, ?, ?, ?, ?, FALSE)",
                batchId,
                orderId,
                nextSequence,
                nextSequence,
                "PENDING"
            );
            finalSequence = nextSequence;
        } else if (existingBatchIds.get(0) != batchId) {
            long previousBatchId = existingBatchIds.get(0);
            int nextSequence = nextBatchSequence(batchId);
            jdbcTemplate.update(
                """
                    UPDATE dispatch_batch_items
                    SET batch_id = ?,
                        current_sequence = ?,
                        suggested_sequence = ?,
                        manually_adjusted = TRUE,
                        reordered_by = ?,
                        reordered_at = CURRENT_TIMESTAMP
                    WHERE meal_slot_order_id = ?
                    """,
                batchId,
                nextSequence,
                nextSequence,
                "ADMIN_REASSIGN",
                orderId
            );
            refreshBatchMetrics(previousBatchId);
            finalSequence = nextSequence;
        } else {
            Integer currentSequence = jdbcTemplate.queryForObject(
                "SELECT current_sequence FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
                Integer.class,
                orderId
            );
            finalSequence = currentSequence == null ? 1 : currentSequence;
        }
        refreshBatchMetrics(batchId);
        return finalSequence;
    }

    void removeBatchItem(long orderId) {
        List<Long> batchIds = jdbcTemplate.query(
            "SELECT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
            (rs, rowNum) -> rs.getLong("batch_id"),
            orderId
        );
        if (batchIds.isEmpty()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE meal_slot_order_id = ?", orderId);
        refreshBatchMetrics(batchIds.get(0));
    }

    private int nextBatchSequence(long batchId) {
        Integer sequence = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(current_sequence), 0) + 1 FROM dispatch_batch_items WHERE batch_id = ?",
            Integer.class,
            batchId
        );
        return sequence == null ? 1 : sequence;
    }

    private void refreshBatchMetrics(long batchId) {
        jdbcTemplate.update(
            """
                UPDATE dispatch_batches
                SET total_count = (
                        SELECT COALESCE(SUM(mso.quantity), 0)
                        FROM dispatch_batch_items dbi
                        JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
                        WHERE dbi.batch_id = ?
                    ),
                    delivered_count = (
                        SELECT COALESCE(SUM(mso.quantity), 0)
                        FROM dispatch_batch_items dbi
                        JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
                        WHERE dbi.batch_id = ?
                          AND dbi.item_status = 'DELIVERED'
                    )
                WHERE id = ?
                """,
            batchId,
            batchId,
            batchId
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
}
