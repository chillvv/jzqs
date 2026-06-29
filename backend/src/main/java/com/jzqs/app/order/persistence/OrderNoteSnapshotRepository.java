package com.jzqs.app.order.persistence;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderNoteSnapshotRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderNoteSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void deleteSnapshots(long mealSlotOrderId) {
        jdbcTemplate.update("DELETE FROM order_notes WHERE meal_slot_order_id = ?", mealSlotOrderId);
    }

    public List<SnapshotInsert> loadCustomerUserNotes(long customerId) {
        return jdbcTemplate.query(
            """
                SELECT content
                FROM customer_notes
                WHERE customer_id = ?
                  AND note_type = 'USER'
                  AND is_active = TRUE
                ORDER BY display_order, id
                """,
            (rs, rowNum) -> new SnapshotInsert("USER", "CUSTOMER_PROFILE", "SNAPSHOT", rs.getString("content")),
            customerId
        );
    }

    public List<SnapshotInsert> loadCustomerMerchantNotes(long customerId, LocalDateTime snapshotTime) {
        return jdbcTemplate.query(
            """
                SELECT scope_type, content
                FROM customer_notes
                WHERE customer_id = ?
                  AND note_type = 'MERCHANT'
                  AND is_active = TRUE
                  AND (
                        scope_type = 'LONG_TERM'
                     OR (scope_type = 'TIME_BOXED' AND start_at <= ? AND end_at >= ?)
                  )
                ORDER BY display_order, id
                """,
            (rs, rowNum) -> new SnapshotInsert(
                "MERCHANT",
                "TIME_BOXED".equals(rs.getString("scope_type")) ? "MERCHANT_TIME_BOXED" : "MERCHANT_PROFILE",
                "SNAPSHOT",
                rs.getString("content")
            ),
            customerId,
            Timestamp.valueOf(snapshotTime),
            Timestamp.valueOf(snapshotTime)
        );
    }

    public void insertSnapshot(long mealSlotOrderId, long customerId, SnapshotInsert insert, String createdBy) {
        jdbcTemplate.update(
            """
                INSERT INTO order_notes (
                    meal_slot_order_id, customer_id, note_type, source_type, scope_type, content, effective_status, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """,
            mealSlotOrderId,
            customerId,
            insert.noteType(),
            insert.sourceType(),
            insert.scopeType(),
            insert.content(),
            createdBy
        );
    }

    public record SnapshotInsert(
        String noteType,
        String sourceType,
        String scopeType,
        String content
    ) {
    }
}
