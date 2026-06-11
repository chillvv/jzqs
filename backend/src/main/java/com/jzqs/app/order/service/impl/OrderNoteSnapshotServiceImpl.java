package com.jzqs.app.order.service.impl;

import com.jzqs.app.order.service.OrderNoteSnapshotService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderNoteSnapshotServiceImpl implements OrderNoteSnapshotService {

    private final JdbcTemplate jdbcTemplate;

    public OrderNoteSnapshotServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void writeOrderSnapshot(
        long mealSlotOrderId,
        long customerId,
        String operatorName,
        String orderUserNote,
        String subscriptionDefaultNote,
        List<String> orderOnceMerchantNotes,
        LocalDateTime snapshotTime
    ) {
        LocalDateTime effectiveSnapshotTime = snapshotTime == null ? LocalDateTime.now() : snapshotTime;
        String createdBy = normalizeOptionalText(operatorName);

        jdbcTemplate.update("DELETE FROM order_notes WHERE meal_slot_order_id = ?", mealSlotOrderId);

        List<SnapshotInsert> inserts = new ArrayList<>();
        inserts.addAll(loadCustomerUserNotes(customerId));

        String normalizedOrderUserNote = normalizeOptionalText(orderUserNote);
        if (normalizedOrderUserNote != null) {
            inserts.add(new SnapshotInsert("USER", "CUSTOMER_ORDER_INPUT", "SNAPSHOT", normalizedOrderUserNote));
        }

        String normalizedSubscriptionDefaultNote = normalizeOptionalText(subscriptionDefaultNote);
        if (normalizedSubscriptionDefaultNote != null) {
            inserts.add(new SnapshotInsert("USER", "SUBSCRIPTION_DEFAULT", "SNAPSHOT", normalizedSubscriptionDefaultNote));
        }

        LinkedHashSet<String> merchantOrderNotes = new LinkedHashSet<>();
        if (orderOnceMerchantNotes != null) {
            for (String merchantOrderNote : orderOnceMerchantNotes) {
                String normalizedMerchantOrderNote = normalizeOptionalText(merchantOrderNote);
                if (normalizedMerchantOrderNote != null) {
                    merchantOrderNotes.add(normalizedMerchantOrderNote);
                }
            }
        }
        for (String merchantOrderNote : merchantOrderNotes) {
            inserts.add(new SnapshotInsert("MERCHANT", "MERCHANT_ORDER_ONCE", "ORDER_ONCE", merchantOrderNote));
        }

        inserts.addAll(loadCustomerMerchantNotes(customerId, effectiveSnapshotTime));

        for (SnapshotInsert insert : inserts) {
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
    }

    private List<SnapshotInsert> loadCustomerUserNotes(long customerId) {
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

    private List<SnapshotInsert> loadCustomerMerchantNotes(long customerId, LocalDateTime snapshotTime) {
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

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private record SnapshotInsert(
        String noteType,
        String sourceType,
        String scopeType,
        String content
    ) {
    }
}
