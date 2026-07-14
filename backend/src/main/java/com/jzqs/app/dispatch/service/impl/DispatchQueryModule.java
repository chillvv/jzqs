package com.jzqs.app.dispatch.service.impl;

import com.jzqs.app.admin.persistence.AdminRowMappers;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingResponse;
import com.jzqs.app.dispatch.api.DispatchAreaOrderItemResponse;
import com.jzqs.app.dispatch.api.DispatchBatchResponse;
import com.jzqs.app.dispatch.api.DispatchBoardItemResponse;
import com.jzqs.app.dispatch.api.DispatchExceptionItemResponse;
import com.jzqs.app.dispatch.api.DispatchOverviewResponse;
import com.jzqs.app.dispatch.api.DispatchPendingItemResponse;
import com.jzqs.app.dispatch.api.DispatchReassignmentResponse;
import com.jzqs.app.dispatch.api.DispatchRiderProgressResponse;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class DispatchQueryModule {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final DispatchAssignmentModule dispatchAssignmentModule;

    DispatchQueryModule(
        JdbcTemplate jdbcTemplate,
        DispatchAssignmentModule dispatchAssignmentModule
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatchAssignmentModule = dispatchAssignmentModule;
    }

    PageResponse<DispatchBoardItemResponse> board() {
        String sql = """
            SELECT
                da.id AS dispatch_id,
                mso.id AS order_id,
                c.name AS customer_name,
                ca.address_line AS delivery_address,
                da.rider_name,
                da.area_code,
                da.status AS delivery_status,
                CASE WHEN dr.id IS NULL THEN 'PENDING' ELSE 'UPLOADED' END AS receipt_status,
                CASE WHEN dr.id IS NULL THEN '绛夊緟涓婁紶' ELSE '[鏌ョ湅鍥炴墽鍥剧墖]' END AS receipt_label,
                CASE WHEN da.status = 'DELIVERED' THEN FALSE ELSE TRUE END AS can_notify_customer
            FROM dispatch_assignments da
            JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
            ORDER BY da.id
            """;
        List<DispatchBoardItemResponse> items = jdbcTemplate.query(sql, AdminRowMappers.DISPATCH_BOARD);
        return PageResponse.of(items, 1, 20, items.size());
    }

    List<DispatchBatchResponse> batches(String serveDate, String mealPeriod) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                db.id AS batch_id,
                db.serve_date AS serve_date,
                db.meal_period,
                db.rider_profile_id,
                rp.rider_name,
                db.area_code,
                db.batch_status,
                db.total_count,
                db.delivered_count,
                db.current_sequence,
                (
                    SELECT c.name
                    FROM dispatch_batch_items dbi
                    JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
                    JOIN daily_orders doo ON doo.id = mso.daily_order_id
                    JOIN customers c ON c.id = doo.customer_id
                    WHERE dbi.batch_id = db.id AND dbi.current_sequence = db.current_sequence
                ) AS current_customer_name,
                (
                    SELECT c.name
                    FROM dispatch_batch_items dbi
                    JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
                    JOIN daily_orders doo ON doo.id = mso.daily_order_id
                    JOIN customers c ON c.id = doo.customer_id
                    WHERE dbi.batch_id = db.id AND dbi.current_sequence = db.current_sequence + 1
                ) AS next_customer_name
            FROM dispatch_batches db
            JOIN rider_profiles rp ON rp.id = db.rider_profile_id
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        if (serveDate != null && !serveDate.isBlank()) {
            sql.append(" AND db.serve_date = ?");
            args.add(LocalDate.parse(serveDate));
        }
        if (mealPeriod != null && !mealPeriod.isBlank()) {
            sql.append(" AND db.meal_period = ?");
            args.add(mealPeriod);
        }
        sql.append(" ORDER BY db.serve_date DESC, db.meal_period, db.id");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DispatchBatchResponse(
            rs.getLong("batch_id"),
            rs.getDate("serve_date").toLocalDate().toString(),
            rs.getString("meal_period"),
            rs.getLong("rider_profile_id"),
            rs.getString("rider_name"),
            rs.getString("area_code"),
            rs.getString("batch_status"),
            rs.getInt("total_count"),
            rs.getInt("delivered_count"),
            rs.getInt("current_sequence"),
            rs.getString("current_customer_name"),
            rs.getString("next_customer_name")
        ), args.toArray());
    }

    DispatchOverviewResponse overview(String mealPeriod, String serveDate) {
        LocalDate targetDate = serveDate == null || serveDate.isEmpty()
            ? LocalDate.now()
            : LocalDate.parse(serveDate);
        String finalMealPeriod = normalizedMealPeriod(mealPeriod);

        dispatchAssignmentModule.ensureRememberedAssignments(mealPeriod);
        int pendingCount = queryCount(
            """
                SELECT COALESCE(SUM(mso.quantity), 0)
                FROM meal_slot_orders mso
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id = mso.id
                LEFT JOIN rider_address_bindings rab ON rab.customer_id = doo.customer_id AND rab.address_id = mso.address_id
                WHERE mso.status = 'PENDING_DISPATCH'
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                  AND da.id IS NULL
                  AND rab.id IS NULL
                """,
            targetDate,
            finalMealPeriod,
            finalMealPeriod
        );
        int dispatchingCount = queryCount(
            """
                SELECT COALESCE(SUM(mso.quantity), 0)
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE da.status = 'DISPATCHING'
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                """,
            targetDate,
            finalMealPeriod,
            finalMealPeriod
        );
        int missingRiderAreaCount = queryCount(
            """
                SELECT COUNT(DISTINCT da.area_code)
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE da.status = 'AREA_ASSIGNED'
                  AND da.rider_name IS NULL
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                """,
            targetDate,
            finalMealPeriod,
            finalMealPeriod
        );
        return new DispatchOverviewResponse(
            pendingCount,
            dispatchingCount,
            missingRiderAreaCount
        );
    }

    List<DispatchRiderProgressResponse> riderProgress(String mealPeriod, String serveDate) {
        LocalDate targetDate = serveDate == null || serveDate.isEmpty()
            ? LocalDate.now()
            : LocalDate.parse(serveDate);
        String finalMealPeriod = normalizedMealPeriod(mealPeriod);
        List<RiderProgressRow> rows = jdbcTemplate.query(
            """
                SELECT
                    da.rider_name,
                    da.area_code,
                    da.meal_slot_order_id AS order_id,
                    COALESCE(NULLIF(da.sequence_number, 0), dbi.current_sequence, 0) AS sequence_number,
                    da.status AS delivery_status,
                    mso.quantity
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                LEFT JOIN dispatch_batch_items dbi ON dbi.meal_slot_order_id = da.meal_slot_order_id
                WHERE doo.serve_date = ?
                  AND da.rider_name IS NOT NULL
                  AND da.rider_name <> ''
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                ORDER BY da.rider_name, da.area_code, da.sequence_number, da.id
                """,
            (rs, rowNum) -> new RiderProgressRow(
                rs.getString("rider_name"),
                rs.getString("area_code"),
                rs.getLong("order_id"),
                rs.getInt("sequence_number"),
                rs.getString("delivery_status"),
                rs.getInt("quantity")
            ),
            targetDate,
            finalMealPeriod,
            finalMealPeriod
        );
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> exceptionCounts = loadRiderExceptionCounts(targetDate, finalMealPeriod);
        List<DispatchRiderProgressResponse> responses = new ArrayList<>();
        RiderProgressAccumulator current = null;
        for (RiderProgressRow row : rows) {
            String key = riderProgressKey(row.riderName(), row.areaCode());
            if (current == null || !current.matches(key)) {
                if (current != null) {
                    responses.add(current.toResponse(exceptionCounts.getOrDefault(current.key(), 0)));
                }
                current = new RiderProgressAccumulator(key, row.riderName(), row.areaCode());
            }
            current.add(row);
        }
        if (current != null) {
            responses.add(current.toResponse(exceptionCounts.getOrDefault(current.key(), 0)));
        }
        return responses;
    }

    List<DispatchExceptionItemResponse> exceptions() {
        String sql = """
            SELECT
                mso.id AS meal_slot_order_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                ca.address_line AS delivery_address,
                COALESCE(rab.area_code, ca.area_code) AS suggested_area_code,
                rp_default.rider_name AS suggested_rider_name,
                CASE
                    WHEN rab.id IS NULL THEN 'NEW_ADDRESS'
                    WHEN dab.id IS NULL THEN 'AREA_RULE_MISSING'
                    WHEN rp_default.id IS NULL THEN 'RIDER_UNAVAILABLE'
                    ELSE 'CONFLICT_REQUIRES_CONFIRM'
                END AS exception_type,
                CASE
                    WHEN rab.id IS NULL THEN '鏂板湴鍧€锛屽皻鏈‘璁ゅ尯鍩?'
                    WHEN dab.id IS NULL THEN '璇ュ尯鍩熸湭璁剧疆榛樿楠戞墜'
                    WHEN rp_default.id IS NULL THEN '榛樿楠戞墜褰撳墠涓嶅彲娲惧崟'
                    ELSE '璇ュ湴鍧€闇€瑕侀噸鏂扮‘璁ゅ尯鍩熷綊灞?'
                END AS reason,
                CASE WHEN rab.id IS NULL THEN FALSE ELSE TRUE END AS remembered_address
            FROM meal_slot_orders mso
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            JOIN customers c ON c.id = doo.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN rider_address_bindings rab ON rab.customer_id = doo.customer_id AND rab.address_id = mso.address_id
            LEFT JOIN dispatch_area_bindings dab ON dab.area_code = rab.area_code
            LEFT JOIN rider_profiles rp_default
                ON rp_default.id = dab.default_rider_profile_id
               AND rp_default.auth_status = 'ACTIVE'
               AND rp_default.employment_status = 'ACTIVE'
            LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id = mso.id
            WHERE mso.status = 'PENDING_DISPATCH' AND da.id IS NULL
            ORDER BY mso.id
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DispatchExceptionItemResponse(
            rs.getLong("meal_slot_order_id"),
            rs.getString("exception_type"),
            rs.getString("reason"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("delivery_address"),
            rs.getString("suggested_area_code"),
            rs.getString("suggested_rider_name"),
            rs.getBoolean("remembered_address")
        ));
    }

    List<DispatchPendingItemResponse> pendingItems(String mealPeriod, String serveDate) {
        LocalDate targetDate = serveDate == null || serveDate.isEmpty()
            ? LocalDate.now()
            : LocalDate.parse(serveDate);
        String finalMealPeriod = normalizedMealPeriod(mealPeriod);

        dispatchAssignmentModule.ensureRememberedAssignments(mealPeriod);
        String sql = """
            SELECT
                mso.id AS order_id,
                c.name AS customer_name,
                ca.address_line AS delivery_address
            FROM meal_slot_orders mso
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            JOIN customers c ON c.id = doo.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN rider_address_bindings rab ON rab.customer_id = doo.customer_id AND rab.address_id = mso.address_id
            LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id = mso.id
            WHERE mso.status = 'PENDING_DISPATCH'
              AND doo.serve_date = ?
              AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
              AND da.id IS NULL
              AND rab.id IS NULL
            ORDER BY mso.id
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new DispatchPendingItemResponse(
            rs.getLong("order_id"),
            rs.getString("customer_name"),
            rs.getString("delivery_address")
        ), targetDate, finalMealPeriod, finalMealPeriod);
    }

    List<DispatchAreaBindingResponse> areaBindings(String mealPeriod, String serveDate) {
        LocalDate targetDate = serveDate == null || serveDate.isEmpty()
            ? LocalDate.now()
            : LocalDate.parse(serveDate);

        String finalMealPeriod = normalizedMealPeriod(mealPeriod);
        return jdbcTemplate.query(
            """
                SELECT
                    dab.area_code,
                    dab.keywords,
                    dab.default_rider_profile_id,
                    rp_default.rider_name AS default_rider_name,
                    dab.updated_by,
                    dab.updated_at AS updated_at
                FROM dispatch_area_bindings dab
                LEFT JOIN rider_profiles rp_default ON rp_default.id = dab.default_rider_profile_id
                ORDER BY dab.area_code
                """,
            (rs, rowNum) -> {
                String areaCode = rs.getString("area_code");
                List<DispatchAreaOrderItemResponse> orders = findAreaOrders(areaCode, finalMealPeriod, targetDate);
                String currentRiderName = orders.stream()
                    .map(DispatchAreaOrderItemResponse::riderName)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElse(rs.getString("default_rider_name"));
                boolean missingRider = !orders.isEmpty() && (currentRiderName == null || currentRiderName.isBlank());
                return new DispatchAreaBindingResponse(
                    areaCode,
                    rs.getString("keywords"),
                    rs.getObject("default_rider_profile_id") == null ? null : rs.getLong("default_rider_profile_id"),
                    rs.getString("default_rider_name"),
                    missingRider ? null : currentRiderName,
                    orders.stream().mapToInt(item -> Math.max(item.quantity(), 1)).sum(),
                    missingRider,
                    orders,
                    rs.getString("updated_by"),
                    formatTimestamp(rs.getTimestamp("updated_at"))
                );
            }
        );
    }

    List<DispatchReassignmentResponse> recentReassignments(String serveDate) {
        LocalDateTime retentionCutoff = LocalDateTime.now().minusDays(30);
        StringBuilder sql = new StringBuilder(
            """
                SELECT
                    id,
                    reassign_level,
                    target_id,
                    from_rider_name,
                    to_rider_name,
                    to_area_code,
                    serve_date,
                    meal_period,
                    sync_default_binding,
                    reason,
                    created_by,
                    created_at
                FROM dispatch_reassignments
                WHERE created_at >= ?
                """
        );
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(retentionCutoff));
        if (serveDate != null && !serveDate.isBlank()) {
            sql.append(" AND serve_date = ?");
            args.add(LocalDate.parse(serveDate));
        }
        sql.append(" ORDER BY created_at DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DispatchReassignmentResponse(
            rs.getLong("id"),
            rs.getString("reassign_level"),
            rs.getLong("target_id"),
            rs.getString("from_rider_name"),
            rs.getString("to_rider_name"),
            rs.getString("to_area_code"),
            rs.getDate("serve_date").toLocalDate().toString(),
            rs.getString("meal_period"),
            rs.getBoolean("sync_default_binding"),
            rs.getString("reason"),
            rs.getString("created_by"),
            formatTimestamp(rs.getTimestamp("created_at"))
        ), args.toArray());
    }

    private int queryCount(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private String normalizedMealPeriod(String mealPeriod) {
        return mealPeriod == null || mealPeriod.isBlank() ? null : mealPeriod.trim().toUpperCase();
    }

    private List<DispatchAreaOrderItemResponse> findAreaOrders(String areaCode, String mealPeriod, LocalDate serveDate) {
        List<DispatchAreaOrderRow> rows = jdbcTemplate.query(
            """
                SELECT
                    mso.id AS order_id,
                    COALESCE(NULLIF(da.sequence_number, 0), dbi.current_sequence, 0) AS sequence_number,
                    c.name AS customer_name,
                    c.phone AS customer_phone,
                    ca.address_line AS delivery_address,
                    da.status AS delivery_status,
                    da.rider_name,
                    COALESCE(mso.user_note, mso.note, '') AS user_note,
                    COALESCE(mso.merchant_remark, '') AS merchant_remark,
                    COALESCE(ari.reference_image_url, '') AS reference_image_url,
                    COALESCE(dr.receipt_url, '') AS receipt_url,
                    COALESCE(dr.receipt_note, '') AS receipt_note,
                    dr.delivered_at,
                    mso.quantity
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                JOIN customers c ON c.id = doo.customer_id
                JOIN customer_addresses ca ON ca.id = mso.address_id
                LEFT JOIN dispatch_batch_items dbi ON dbi.meal_slot_order_id = da.meal_slot_order_id
                LEFT JOIN address_reference_images ari ON ari.customer_address_id = mso.address_id
                LEFT JOIN (
                    SELECT meal_slot_order_id, receipt_url, receipt_note, delivered_at
                    FROM delivery_receipts dr_inner
                    WHERE dr_inner.id = (
                        SELECT MAX(id) FROM delivery_receipts WHERE meal_slot_order_id = dr_inner.meal_slot_order_id
                    )
                ) dr ON dr.meal_slot_order_id = mso.id
                WHERE da.area_code = ?
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                ORDER BY
                    CASE WHEN da.status = 'DELIVERED' THEN 1 ELSE 0 END,
                    COALESCE(NULLIF(da.sequence_number, 0), dbi.current_sequence, 0),
                    da.id
                """,
            (rs, rowNum) -> new DispatchAreaOrderRow(
                rs.getLong("order_id"),
                rs.getInt("sequence_number"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                rs.getString("delivery_address"),
                rs.getString("delivery_status"),
                rs.getString("rider_name"),
                rs.getString("user_note"),
                rs.getString("merchant_remark"),
                rs.getString("reference_image_url"),
                rs.getString("receipt_url"),
                rs.getString("receipt_note"),
                rs.getTimestamp("delivered_at"),
                rs.getInt("quantity")
            ),
            areaCode,
            serveDate,
            mealPeriod,
            mealPeriod
        );
        Map<Long, OrderNoteProjection> projections = loadOrderNoteProjections(
            rows.stream().map(DispatchAreaOrderRow::orderId).toList()
        );
        return rows.stream()
            .map(row -> toDispatchAreaOrderItem(row, projections.get(row.orderId())))
            .toList();
    }

    private DispatchAreaOrderItemResponse toDispatchAreaOrderItem(DispatchAreaOrderRow row, OrderNoteProjection projection) {
        String userNote = resolveProjectedUserNote(projection, row.userNote());
        String merchantRemark = resolveProjectedAdminNote(projection, row.merchantRemark());
        List<String> attentionSources = buildAttentionSources(userNote, merchantRemark);
        return new DispatchAreaOrderItemResponse(
            row.orderId(),
            row.sequenceNumber(),
            row.customerName(),
            row.customerPhone(),
            row.deliveryAddress(),
            row.deliveryStatus(),
            row.riderName(),
            userNote,
            merchantRemark,
            !attentionSources.isEmpty(),
            attentionSources,
            buildAttentionLabel(attentionSources),
            row.referenceImageUrl(),
            row.receiptUrl(),
            row.receiptNote(),
            formatTimestamp(row.deliveredAt()),
            row.quantity()
        );
    }

    private Map<String, Integer> loadRiderExceptionCounts(LocalDate serveDate, String mealPeriod) {
        List<RiderExceptionCountRow> rows = jdbcTemplate.query(
            """
                SELECT
                    de.rider_name,
                    da.area_code,
                    COUNT(DISTINCT de.meal_slot_order_id) AS exception_count
                FROM delivery_exceptions de
                JOIN dispatch_assignments da ON da.meal_slot_order_id = de.meal_slot_order_id
                JOIN meal_slot_orders mso ON mso.id = de.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE de.resolved = FALSE
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                GROUP BY de.rider_name, da.area_code
                """,
            (rs, rowNum) -> new RiderExceptionCountRow(
                rs.getString("rider_name"),
                rs.getString("area_code"),
                rs.getInt("exception_count")
            ),
            serveDate,
            mealPeriod,
            mealPeriod
        );
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RiderExceptionCountRow row : rows) {
            counts.put(
                riderProgressKey(row.riderName(), row.areaCode()),
                row.exceptionCount()
            );
        }
        return counts;
    }

    private Map<Long, OrderNoteProjection> loadOrderNoteProjections(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", orderIds.stream().map(id -> "?").toList());
        List<OrderNoteRow> rows = jdbcTemplate.query(
            """
                SELECT meal_slot_order_id, note_type, content
                FROM order_notes
                WHERE effective_status = 'ACTIVE'
                  AND meal_slot_order_id IN (
            """
                + placeholders
                + """
                  )
                ORDER BY meal_slot_order_id, created_at, id
                """,
            (rs, rowNum) -> new OrderNoteRow(
                rs.getLong("meal_slot_order_id"),
                rs.getString("note_type"),
                rs.getString("content")
            ),
            orderIds.toArray()
        );
        Map<Long, DispatchNoteAccumulator> accumulators = new LinkedHashMap<>();
        for (OrderNoteRow row : rows) {
            long orderId = row.orderId();
            DispatchNoteAccumulator accumulator = accumulators.computeIfAbsent(orderId, ignored -> new DispatchNoteAccumulator());
            accumulator.add(row.noteType(), row.content());
        }
        Map<Long, OrderNoteProjection> projections = new LinkedHashMap<>();
        for (Long orderId : orderIds) {
            DispatchNoteAccumulator accumulator = accumulators.get(orderId);
            if (accumulator != null) {
                projections.put(orderId, accumulator.toProjection());
            }
        }
        return projections;
    }

    private String resolveProjectedUserNote(OrderNoteProjection projection, String legacyValue) {
        if (projection != null && projection.hasOrderNotes()) {
            return projection.userNote().isBlank() ? "-" : projection.userNote();
        }
        String normalized = normalizeLegacyNote(legacyValue);
        return normalized.isBlank() ? "-" : normalized;
    }

    private String resolveProjectedAdminNote(OrderNoteProjection projection, String legacyValue) {
        if (projection != null && projection.hasOrderNotes()) {
            return projection.adminNote();
        }
        return normalizeLegacyNote(legacyValue);
    }

    private String normalizeLegacyNote(String value) {
        String normalized = stringValue(value);
        return "-".equals(normalized) ? "" : normalized;
    }

    private List<String> buildAttentionSources(String userNote, String adminNote) {
        List<String> sources = new ArrayList<>();
        if (!normalizeLegacyNote(userNote).isBlank()) {
            sources.add("USER_NOTE");
        }
        if (!normalizeLegacyNote(adminNote).isBlank()) {
            sources.add("MERCHANT_NOTE");
        }
        return List.copyOf(sources);
    }

    private String buildAttentionLabel(List<String> attentionSources) {
        return attentionSources.isEmpty() ? "" : "闇€鐣欐剰";
    }

    private String riderProgressKey(String riderName, String areaCode) {
        return riderName + "@" + areaCode;
    }

    private String formatTimestamp(Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record RiderExceptionCountRow(String riderName, String areaCode, int exceptionCount) {
    }

    private record OrderNoteRow(long orderId, String noteType, String content) {
    }

    private record RiderProgressRow(
        String riderName,
        String areaCode,
        long orderId,
        int sequenceNumber,
        String deliveryStatus,
        int quantity
    ) {
    }

    private record DispatchAreaOrderRow(
        long orderId,
        int sequenceNumber,
        String customerName,
        String customerPhone,
        String deliveryAddress,
        String deliveryStatus,
        String riderName,
        String userNote,
        String merchantRemark,
        String referenceImageUrl,
        String receiptUrl,
        String receiptNote,
        Timestamp deliveredAt,
        int quantity
    ) {
    }

    private record OrderNoteProjection(String userNote, String adminNote, boolean hasOrderNotes) {
    }

    private static final class DispatchNoteAccumulator {
        private final List<String> userNotes = new ArrayList<>();
        private final List<String> merchantNotes = new ArrayList<>();

        private void add(String noteType, String content) {
            String normalized = content == null ? "" : content.trim();
            if (normalized.isBlank() || "-".equals(normalized)) {
                return;
            }
            List<String> target = "MERCHANT".equals(noteType) ? merchantNotes : userNotes;
            if (!target.contains(normalized)) {
                target.add(normalized);
            }
        }

        private OrderNoteProjection toProjection() {
            return new OrderNoteProjection(
                String.join(" / ", userNotes),
                String.join(" / ", merchantNotes),
                true
            );
        }
    }

    private static final class RiderProgressAccumulator {
        private final String key;
        private final String riderName;
        private final String areaCode;
        private int totalCount;
        private int completedCount;
        private long currentOrderId;
        private int currentSequenceNumber;
        private long nextOrderId;
        private boolean currentCaptured;

        private RiderProgressAccumulator(String key, String riderName, String areaCode) {
            this.key = key;
            this.riderName = riderName;
            this.areaCode = areaCode;
        }

        private boolean matches(String otherKey) {
            return key.equals(otherKey);
        }

        private String key() {
            return key;
        }

        private void add(RiderProgressRow row) {
            totalCount += Math.max(row.quantity(), 1);
            if ("DELIVERED".equalsIgnoreCase(row.deliveryStatus())) {
                completedCount += Math.max(row.quantity(), 1);
                return;
            }
            if (!currentCaptured) {
                currentOrderId = row.orderId();
                currentSequenceNumber = row.sequenceNumber();
                currentCaptured = true;
            } else if (nextOrderId == 0L) {
                nextOrderId = row.orderId();
            }
        }

        private DispatchRiderProgressResponse toResponse(int exceptionCount) {
            return new DispatchRiderProgressResponse(
                riderName,
                areaCode,
                completedCount,
                totalCount,
                currentOrderId,
                currentSequenceNumber,
                nextOrderId,
                Math.max(totalCount - completedCount, 0),
                exceptionCount
            );
        }
    }
}
