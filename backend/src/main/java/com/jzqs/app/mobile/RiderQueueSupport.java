package com.jzqs.app.mobile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.common.util.OrderNoteTexts;
import com.jzqs.app.mobile.api.RiderDeliveryExceptionReportResponse;
import com.jzqs.app.mobile.api.RiderQueueItemActionResponse;
import com.jzqs.app.mobile.api.RiderQueueItemResponse;
import com.jzqs.app.mobile.api.RiderQueueReorderResponse;
import com.jzqs.app.mobile.api.RiderBatchSummaryResponse;
import com.jzqs.app.mobile.api.RiderTaskItemResponse;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
class RiderQueueSupport {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RealtimeAudienceModule realtimeAudienceModule;

    RiderQueueSupport(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, RealtimeAudienceModule realtimeAudienceModule) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.realtimeAudienceModule = realtimeAudienceModule;
    }

    PageResponse<RiderTaskItemResponse> riderTasks(Long riderId) {
        LocalDate today = LocalDate.now();
        List<RiderTaskItemResponse> items = jdbcTemplate.query("""
            SELECT
                da.id AS dispatch_id,
                mso.id AS meal_slot_order_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                CASE WHEN ca.door_number IS NOT NULL AND ca.door_number <> ''
                     THEN CONCAT(ca.address_line, ' ', ca.door_number)
                     ELSE ca.address_line END AS delivery_address,
                ca.latitude AS latitude,
                ca.longitude AS longitude,
                mso.meal_period AS production_meal_period,
                COALESCE(mso.delivery_meal_period, mso.meal_period) AS delivery_meal_period,
                COALESCE(ms.meal_name, CASE WHEN mso.meal_period = 'LUNCH' THEN '待配置午餐' ELSE '待配置晚餐' END) AS meal_name,
                COALESCE(mso.user_note, mso.note, '-') AS note,
                da.status AS delivery_status,
                CASE WHEN dr.id IS NULL THEN 'PENDING' ELSE 'UPLOADED' END AS receipt_status,
                COALESCE(dr.receipt_url, '') AS receipt_url
            FROM dispatch_assignments da
            JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN menu_week_items ms ON ms.serve_date = do.serve_date
                AND ms.meal_period = mso.meal_period
                AND ms.slot_status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM menu_weeks mw2 WHERE mw2.id = ms.week_id AND mw2.status = 'PUBLISHED')
            LEFT JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
            WHERE da.rider_profile_id = ?
              AND do.serve_date = ?
            ORDER BY CASE WHEN COALESCE(mso.delivery_meal_period, mso.meal_period) = 'LUNCH' THEN 1 ELSE 2 END,
                     COALESCE(da.sequence_number, 2147483647),
                     da.id
            """, (rs, rowNum) -> new RiderTaskItemResponse(
            rs.getLong("dispatch_id"),
            rs.getLong("meal_slot_order_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("delivery_address"),
            rs.getString("delivery_meal_period"),
            rs.getString("production_meal_period"),
            rs.getString("delivery_meal_period"),
            rs.getString("meal_name"),
            rs.getString("note"),
            rs.getString("delivery_status"),
            rs.getString("receipt_status"),
            rs.getString("receipt_url")
        ), riderId, today);
        return PageResponse.of(items, 1, 20, items.size());
    }

    RiderBatchSummaryResponse riderSummary(Long riderId, String serveDate) {
        LocalDate targetDate = resolveServeDateOrToday(serveDate);
        List<RiderBatchSummaryResponse.BatchCardResponse> cards = jdbcTemplate.query("""
            SELECT
                db.id AS batch_id,
                db.meal_period,
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
            WHERE db.rider_profile_id = ?
              AND db.serve_date = ?
            ORDER BY CASE WHEN db.meal_period = 'LUNCH' THEN 1 ELSE 2 END, db.id DESC
            """, (rs, rowNum) -> new RiderBatchSummaryResponse.BatchCardResponse(
            rs.getLong("batch_id"),
            rs.getString("meal_period"),
            rs.getString("batch_status"),
            rs.getInt("total_count"),
            rs.getInt("delivered_count"),
            Math.max(rs.getInt("total_count") - rs.getInt("delivered_count"), 0),
            rs.getInt("current_sequence"),
            rs.getString("current_customer_name"),
            rs.getString("next_customer_name")
        ), riderId, targetDate);
        RiderBatchSummaryResponse.BatchCardResponse lunch = cards.stream().filter(item -> "LUNCH".equals(item.mealPeriod())).findFirst().orElse(null);
        RiderBatchSummaryResponse.BatchCardResponse dinner = cards.stream().filter(item -> "DINNER".equals(item.mealPeriod())).findFirst().orElse(null);
        int totalCount = cards.stream().mapToInt(RiderBatchSummaryResponse.BatchCardResponse::totalCount).sum();
        int deliveredCount = cards.stream().mapToInt(RiderBatchSummaryResponse.BatchCardResponse::deliveredCount).sum();
        return new RiderBatchSummaryResponse(
            resolveRiderName(riderId),
            totalCount,
            deliveredCount,
            Math.max(totalCount - deliveredCount, 0),
            lunch,
            dinner
        );
    }

    PageResponse<RiderTaskItemResponse> riderCompletedToday(Long riderId) {
        LocalDate today = LocalDate.now();
        List<RiderTaskItemResponse> items = jdbcTemplate.query("""
            SELECT
                da.id AS dispatch_id,
                mso.id AS meal_slot_order_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                CASE WHEN ca.door_number IS NOT NULL AND ca.door_number <> ''
                     THEN CONCAT(ca.address_line, ' ', ca.door_number)
                     ELSE ca.address_line END AS delivery_address,
                ca.latitude AS latitude,
                ca.longitude AS longitude,
                mso.meal_period AS production_meal_period,
                COALESCE(mso.delivery_meal_period, mso.meal_period) AS delivery_meal_period,
                COALESCE(ms.meal_name, CASE WHEN mso.meal_period = 'LUNCH' THEN '待配置午餐' ELSE '待配置晚餐' END) AS meal_name,
                COALESCE(mso.user_note, mso.note, '-') AS note,
                da.status AS delivery_status,
                'UPLOADED' AS receipt_status,
                COALESCE(dr.receipt_url, '') AS receipt_url
            FROM dispatch_assignments da
            JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN menu_week_items ms ON ms.serve_date = do.serve_date
                AND ms.meal_period = mso.meal_period
                AND ms.slot_status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM menu_weeks mw2 WHERE mw2.id = ms.week_id AND mw2.status = 'PUBLISHED')
            JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
            WHERE da.rider_profile_id = ?
              AND do.serve_date = ?
              AND DATE(dr.delivered_at) = ?
              AND da.status = 'DELIVERED'
            ORDER BY dr.delivered_at DESC
            """, (rs, rowNum) -> new RiderTaskItemResponse(
            rs.getLong("dispatch_id"),
            rs.getLong("meal_slot_order_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("delivery_address"),
            rs.getString("delivery_meal_period"),
            rs.getString("production_meal_period"),
            rs.getString("delivery_meal_period"),
            rs.getString("meal_name"),
            rs.getString("note"),
            rs.getString("delivery_status"),
            rs.getString("receipt_status"),
            rs.getString("receipt_url")
        ), riderId, today, today);
        return PageResponse.of(items, 1, 20, items.size());
    }

    RiderDeliveryExceptionReportResponse reportDeliveryException(
        long mealSlotOrderId,
        Long riderId,
        String exceptionType,
        String exceptionNote,
        List<String> exceptionImages
    ) {
        DeliveryExceptionOrderInfo orderInfo = jdbcTemplate.query("""
            SELECT
                rp.id AS rider_profile_id,
                c.phone AS customer_phone,
                CASE WHEN ca.door_number IS NOT NULL AND ca.door_number <> ''
                     THEN CONCAT(ca.address_line, ' ', ca.door_number)
                     ELSE ca.address_line END AS delivery_address
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            CROSS JOIN rider_profiles rp
            WHERE mso.id = ? AND rp.id = ?
            """,
            ps -> {
                ps.setLong(1, mealSlotOrderId);
                ps.setLong(2, riderId);
            },
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new DeliveryExceptionOrderInfo(
                    rs.getLong("rider_profile_id"),
                    rs.getString("customer_phone"),
                    rs.getString("delivery_address")
                );
            }
        );
        if (orderInfo == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应订单或骑手信息");
        }
        String imagesJson = null;
        if (exceptionImages != null && !exceptionImages.isEmpty()) {
            try {
                imagesJson = objectMapper.writeValueAsString(exceptionImages);
            } catch (JsonProcessingException ignored) {
            }
        }
        long exceptionId = insertAndReturnId("""
            INSERT INTO delivery_exceptions (
                meal_slot_order_id,
                rider_profile_id,
                rider_name,
                exception_type,
                exception_note,
                customer_phone,
                delivery_address,
                exception_images,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """,
            mealSlotOrderId,
            orderInfo.riderProfileId(),
            resolveRiderName(riderId),
            exceptionType,
            exceptionNote,
            orderInfo.customerPhone(),
            orderInfo.deliveryAddress(),
            imagesJson
        );
        publishRiderEvent("dispatch.exception.changed", resolveRiderName(riderId), mealSlotOrderId);
        return new RiderDeliveryExceptionReportResponse(exceptionId, "REPORTED", "异常已上报，请等待处理");
    }

    PageResponse<RiderQueueItemResponse> riderQueue(Long riderId, String serveDate) {
        LocalDate targetDate = resolveServeDateOrToday(serveDate);
        ensureRiderQueueMaterialized(riderId, targetDate);
        List<RiderQueueRow> rows = jdbcTemplate.query("""
            SELECT
                COALESCE(dbi.id, 0) AS batch_item_id,
                COALESCE(db.id, 0) AS batch_id,
                mso.id AS meal_slot_order_id,
                mso.address_id AS address_id,
                COALESCE(NULLIF(dbi.current_sequence, 0), NULLIF(da.sequence_number, 0), 0) AS current_sequence,
                c.name AS customer_name,
                c.phone AS customer_phone,
                CASE WHEN ca.door_number IS NOT NULL AND ca.door_number <> ''
                     THEN CONCAT(ca.address_line, ' ', ca.door_number)
                     ELSE ca.address_line END AS delivery_address,
                ca.latitude AS latitude,
                ca.longitude AS longitude,
                mso.meal_period AS production_meal_period,
                COALESCE(mso.delivery_meal_period, mso.meal_period) AS delivery_meal_period,
                COALESCE(ms.meal_name, CASE WHEN mso.meal_period = 'LUNCH' THEN '待配置午餐' ELSE '待配置晚餐' END) AS meal_name,
                mso.quantity,
                COALESCE(mso.user_note, mso.note, '-') AS note,
                COALESCE(mso.merchant_remark, '') AS merchant_remark,
                COALESCE(
                    dbi.item_status,
                    CASE
                        WHEN da.status = 'DELIVERED' THEN 'DELIVERED'
                        WHEN da.status = 'DEFERRED' THEN 'DEFERRED'
                        ELSE 'PENDING'
                    END
                ) AS item_status,
                CASE WHEN dr.id IS NULL THEN 'PENDING' ELSE 'UPLOADED' END AS receipt_status,
                COALESCE(dr.receipt_url, '') AS receipt_url,
                COALESCE(dr.receipt_note, '') AS receipt_note,
                COALESCE(ari.reference_image_url, '') AS reference_image_url
            FROM dispatch_assignments da
            JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            JOIN customers c ON c.id = doo.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN address_reference_images ari ON ari.customer_address_id = mso.address_id
            LEFT JOIN dispatch_batch_items dbi ON dbi.meal_slot_order_id = mso.id
            LEFT JOIN dispatch_batches db ON db.id = dbi.batch_id
            LEFT JOIN menu_week_items ms ON ms.serve_date = doo.serve_date
                AND ms.meal_period = mso.meal_period
                AND ms.slot_status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM menu_weeks mw2 WHERE mw2.id = ms.week_id AND mw2.status = 'PUBLISHED')
            LEFT JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
            WHERE da.rider_profile_id = ?
              AND doo.serve_date = ?
            ORDER BY CASE WHEN COALESCE(mso.delivery_meal_period, mso.meal_period) = 'LUNCH' THEN 1 ELSE 2 END,
                     COALESCE(NULLIF(dbi.current_sequence, 0), NULLIF(da.sequence_number, 0), 2147483647) ASC,
                     da.id ASC
            """, (rs, rowNum) -> new RiderQueueRow(
            rs.getLong("batch_item_id"),
            rs.getLong("batch_id"),
            rs.getLong("meal_slot_order_id"),
            rs.getLong("address_id"),
            rs.getInt("current_sequence"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("delivery_address"),
            rs.getBigDecimal("latitude"),
            rs.getBigDecimal("longitude"),
            rs.getString("production_meal_period"),
            rs.getString("delivery_meal_period"),
            rs.getString("meal_name"),
            rs.getInt("quantity"),
            rs.getString("note"),
            rs.getString("merchant_remark"),
            rs.getString("item_status"),
            rs.getString("receipt_status"),
            rs.getString("receipt_url"),
            rs.getString("receipt_note"),
            rs.getString("reference_image_url")
        ), riderId, targetDate);
        Map<Long, OrderNoteProjection> projections = loadOrderNoteProjections(rows.stream().map(RiderQueueRow::mealSlotOrderId).toList());
        List<RiderQueueItemResponse> items = rows.stream()
            .map(row -> buildRiderQueueItemResponse(row, projections.get(row.mealSlotOrderId())))
            .toList();
        return PageResponse.of(items, 1, 50, items.size());
    }

    RiderQueueItemResponse riderQueueItem(long queueItemId, Long riderId, String serveDate, Long mealSlotOrderId) {
        LocalDate targetDate = resolveServeDateOrToday(serveDate);
        ensureRiderQueueMaterialized(riderId, targetDate);
        long resolvedMealSlotOrderId = mealSlotOrderId == null ? 0L : mealSlotOrderId.longValue();
        boolean shouldUseMealSlotOrderFallback = resolvedMealSlotOrderId > 0;
        String detailSql = shouldUseMealSlotOrderFallback
            ? """
            SELECT
                COALESCE(dbi.id, 0) AS batch_item_id,
                COALESCE(db.id, 0) AS batch_id,
                mso.id AS meal_slot_order_id,
                mso.address_id AS address_id,
                COALESCE(NULLIF(dbi.current_sequence, 0), NULLIF(da.sequence_number, 0), 0) AS current_sequence,
                c.name AS customer_name,
                c.phone AS customer_phone,
                CASE WHEN ca.door_number IS NOT NULL AND ca.door_number <> ''
                     THEN CONCAT(ca.address_line, ' ', ca.door_number)
                     ELSE ca.address_line END AS delivery_address,
                ca.latitude AS latitude,
                ca.longitude AS longitude,
                mso.meal_period AS production_meal_period,
                COALESCE(mso.delivery_meal_period, mso.meal_period) AS delivery_meal_period,
                COALESCE(ms.meal_name, CASE WHEN mso.meal_period = 'LUNCH' THEN '待配置午餐' ELSE '待配置晚餐' END) AS meal_name,
                mso.quantity,
                COALESCE(mso.user_note, mso.note, '-') AS note,
                COALESCE(mso.merchant_remark, '') AS merchant_remark,
                COALESCE(
                    dbi.item_status,
                    CASE
                        WHEN da.status = 'DELIVERED' THEN 'DELIVERED'
                        WHEN da.status = 'DEFERRED' THEN 'DEFERRED'
                        ELSE 'PENDING'
                    END
                ) AS item_status,
                CASE WHEN dr.id IS NULL THEN 'PENDING' ELSE 'UPLOADED' END AS receipt_status,
                COALESCE(dr.receipt_url, '') AS receipt_url,
                COALESCE(dr.receipt_note, '') AS receipt_note
            FROM dispatch_assignments da
            JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            JOIN customers c ON c.id = doo.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN dispatch_batch_items dbi ON dbi.meal_slot_order_id = mso.id
            LEFT JOIN dispatch_batches db ON db.id = dbi.batch_id
            LEFT JOIN menu_week_items ms ON ms.serve_date = doo.serve_date
                AND ms.meal_period = mso.meal_period
                AND ms.slot_status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM menu_weeks mw2 WHERE mw2.id = ms.week_id AND mw2.status = 'PUBLISHED')
            LEFT JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
            WHERE mso.id = ?
              AND da.rider_profile_id = ?
              AND doo.serve_date = ?
            """
            : """
            SELECT
                COALESCE(dbi.id, 0) AS batch_item_id,
                COALESCE(db.id, 0) AS batch_id,
                mso.id AS meal_slot_order_id,
                mso.address_id AS address_id,
                COALESCE(NULLIF(dbi.current_sequence, 0), NULLIF(da.sequence_number, 0), 0) AS current_sequence,
                c.name AS customer_name,
                c.phone AS customer_phone,
                CASE WHEN ca.door_number IS NOT NULL AND ca.door_number <> ''
                     THEN CONCAT(ca.address_line, ' ', ca.door_number)
                     ELSE ca.address_line END AS delivery_address,
                ca.latitude AS latitude,
                ca.longitude AS longitude,
                mso.meal_period AS production_meal_period,
                COALESCE(mso.delivery_meal_period, mso.meal_period) AS delivery_meal_period,
                COALESCE(ms.meal_name, CASE WHEN mso.meal_period = 'LUNCH' THEN '待配置午餐' ELSE '待配置晚餐' END) AS meal_name,
                mso.quantity,
                COALESCE(mso.user_note, mso.note, '-') AS note,
                COALESCE(mso.merchant_remark, '') AS merchant_remark,
                COALESCE(
                    dbi.item_status,
                    CASE
                        WHEN da.status = 'DELIVERED' THEN 'DELIVERED'
                        WHEN da.status = 'DEFERRED' THEN 'DEFERRED'
                        ELSE 'PENDING'
                    END
                ) AS item_status,
                CASE WHEN dr.id IS NULL THEN 'PENDING' ELSE 'UPLOADED' END AS receipt_status,
                COALESCE(dr.receipt_url, '') AS receipt_url,
                COALESCE(dr.receipt_note, '') AS receipt_note
            FROM dispatch_assignments da
            JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            JOIN customers c ON c.id = doo.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN dispatch_batch_items dbi ON dbi.meal_slot_order_id = mso.id
            LEFT JOIN dispatch_batches db ON db.id = dbi.batch_id
            LEFT JOIN menu_week_items ms ON ms.serve_date = doo.serve_date
                AND ms.meal_period = mso.meal_period
                AND ms.slot_status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM menu_weeks mw2 WHERE mw2.id = ms.week_id AND mw2.status = 'PUBLISHED')
            LEFT JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
            WHERE dbi.id = ?
              AND da.rider_profile_id = ?
              AND doo.serve_date = ?
            """;
        List<RiderQueueRow> results = jdbcTemplate.query(detailSql, (rs, rowNum) -> new RiderQueueRow(
            rs.getLong("batch_item_id"),
            rs.getLong("batch_id"),
            rs.getLong("meal_slot_order_id"),
            rs.getLong("address_id"),
            rs.getInt("current_sequence"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("delivery_address"),
            rs.getBigDecimal("latitude"),
            rs.getBigDecimal("longitude"),
            rs.getString("production_meal_period"),
            rs.getString("delivery_meal_period"),
            rs.getString("meal_name"),
            rs.getInt("quantity"),
            rs.getString("note"),
            rs.getString("merchant_remark"),
            rs.getString("item_status"),
            rs.getString("receipt_status"),
            rs.getString("receipt_url"),
            rs.getString("receipt_note"),
            ""
        ), shouldUseMealSlotOrderFallback ? resolvedMealSlotOrderId : queueItemId, riderId, targetDate);
        if (results.isEmpty()) {
            return null;
        }
        RiderQueueRow row = results.get(0);
        OrderNoteProjection projection = loadOrderNoteProjections(List.of(row.mealSlotOrderId())).get(row.mealSlotOrderId());
        return buildRiderQueueItemResponse(row, projection);
    }

    RiderQueueReorderResponse reorderRiderQueue(Long riderId, List<Long> batchItemIds) {
        if (batchItemIds == null || batchItemIds.isEmpty()) {
            return new RiderQueueReorderResponse(0, "UNCHANGED");
        }
        List<BatchItemSequenceRow> currentRows = jdbcTemplate.query(
            """
                SELECT id, batch_id, current_sequence
                FROM dispatch_batch_items
                WHERE id IN (%s)
                ORDER BY current_sequence ASC, id ASC
                """.formatted("?,".repeat(batchItemIds.size()).replaceAll(",$", "")),
            ps -> {
                for (int i = 0; i < batchItemIds.size(); i++) {
                    ps.setLong(i + 1, batchItemIds.get(i));
                }
            },
            (rs, rowNum) -> new BatchItemSequenceRow(
                rs.getLong("id"),
                rs.getLong("batch_id"),
                rs.getInt("current_sequence")
            )
        );
        if (currentRows.isEmpty()) {
            return new RiderQueueReorderResponse(0, "UNCHANGED");
        }

        Long batchId = currentRows.get(0).batchId();
        for (BatchItemSequenceRow row : currentRows) {
            if (row.batchId() != batchId.longValue()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "一次只能调整同一配送批次的顺序");
            }
        }

        List<Long> fullBatchOrder = jdbcTemplate.query(
            """
                SELECT id
                FROM dispatch_batch_items
                WHERE batch_id = ?
                ORDER BY current_sequence ASC, id ASC
                """,
            (rs, rowNum) -> rs.getLong("id"),
            batchId
        );
        Map<Long, Boolean> submittedIds = new HashMap<>();
        for (Long batchItemId : batchItemIds) {
            submittedIds.put(batchItemId, Boolean.TRUE);
        }
        List<Long> mergedOrder = new ArrayList<>();
        int reorderedIndex = 0;
        for (Long existingId : fullBatchOrder) {
            if (Boolean.TRUE.equals(submittedIds.get(existingId))) {
                mergedOrder.add(batchItemIds.get(reorderedIndex++));
            } else {
                mergedOrder.add(existingId);
            }
        }

        jdbcTemplate.update("UPDATE dispatch_batch_items SET current_sequence = current_sequence + 1000 WHERE batch_id = ?", batchId);
        int sequence = 1;
        for (Long batchItemId : mergedOrder) {
            jdbcTemplate.update("""
                UPDATE dispatch_batch_items
                SET current_sequence = ?, manually_adjusted = TRUE, reordered_by = ?, reordered_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, sequence++, resolveRiderName(riderId), batchItemId);
        }
        syncDispatchAssignmentsFromBatch(batchId);
        publishRiderEvent("dispatch.queue.changed", resolveRiderName(riderId), batchItemIds.get(0));
        return new RiderQueueReorderResponse(batchItemIds.size(), "REORDERED");
    }

    RiderQueueItemActionResponse deferRiderQueueItem(Long riderId, long batchItemId) {
        RiderBatchItemContext context = requireRiderBatchItem(riderId, batchItemId);
        if ("DELIVERED".equals(context.itemStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "已送达订单不能稍后送");
        }
        if ("DEFERRED".equals(context.itemStatus())) {
            return new RiderQueueItemActionResponse(batchItemId, "DEFERRED", "UNCHANGED");
        }
        int lastSequence = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(current_sequence), 0) FROM dispatch_batch_items WHERE batch_id = ?",
            Integer.class,
            context.batchId()
        );
        jdbcTemplate.update("UPDATE dispatch_batch_items SET current_sequence = -1 WHERE id = ?", batchItemId);
        jdbcTemplate.update("""
            UPDATE dispatch_batch_items
            SET current_sequence = current_sequence - 1,
                manually_adjusted = TRUE,
                reordered_by = ?,
                reordered_at = CURRENT_TIMESTAMP
            WHERE batch_id = ? AND current_sequence > ?
            ORDER BY current_sequence ASC
            """, resolveRiderName(riderId), context.batchId(), context.currentSequence());
        jdbcTemplate.update("""
            UPDATE dispatch_batch_items
            SET current_sequence = ?,
                item_status = 'DEFERRED',
                manually_adjusted = TRUE,
                reordered_by = ?,
                reordered_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, lastSequence, resolveRiderName(riderId), batchItemId);
        refreshRiderBatchState(context.batchId());
        publishRiderEvent("dispatch.queue.changed", resolveRiderName(riderId), batchItemId);
        return new RiderQueueItemActionResponse(batchItemId, "DEFERRED", "DEFERRED");
    }

    RiderQueueItemActionResponse resumeRiderQueueItem(Long riderId, long batchItemId) {
        RiderBatchItemContext context = requireRiderBatchItem(riderId, batchItemId);
        if ("DELIVERED".equals(context.itemStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "已送达订单不能恢复队列");
        }
        if (!"DEFERRED".equals(context.itemStatus())) {
            return new RiderQueueItemActionResponse(batchItemId, context.itemStatus(), "UNCHANGED");
        }
        jdbcTemplate.update("""
            UPDATE dispatch_batch_items
            SET item_status = 'PENDING',
                manually_adjusted = TRUE,
                reordered_by = ?,
                reordered_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, resolveRiderName(riderId), batchItemId);
        refreshRiderBatchState(context.batchId());
        String finalStatus = jdbcTemplate.queryForObject(
            "SELECT item_status FROM dispatch_batch_items WHERE id = ?",
            String.class,
            batchItemId
        );
        publishRiderEvent("dispatch.queue.changed", resolveRiderName(riderId), batchItemId);
        return new RiderQueueItemActionResponse(batchItemId, finalStatus != null ? finalStatus : "PENDING", "RESUMED");
    }

    void refreshQueueStateForOrder(long mealSlotOrderId) {
        List<Long> batchIds = jdbcTemplate.query(
            "SELECT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
            (rs, rowNum) -> rs.getLong("batch_id"),
            mealSlotOrderId
        );
        for (Long batchId : batchIds) {
            refreshRiderBatchState(batchId);
        }
    }

    private RiderQueueItemResponse buildRiderQueueItemResponse(RiderQueueRow row, OrderNoteProjection projection) {
        String note = resolveProjectedUserNote(projection, row.note());
        String merchantRemark = resolveProjectedAdminNote(projection, row.merchantRemark());
        List<String> attentionSources = buildAttentionSources(note, merchantRemark);
        boolean hasAttentionMark = !attentionSources.isEmpty();
        return new RiderQueueItemResponse(
            row.batchItemId(),
            row.batchId(),
            row.mealSlotOrderId(),
            row.addressId(),
            row.currentSequence(),
            row.customerName(),
            row.customerPhone(),
            row.deliveryAddress(),
            row.deliveryMealPeriod(),
            row.productionMealPeriod(),
            row.deliveryMealPeriod(),
            row.mealName(),
            row.quantity(),
            note,
            merchantRemark,
            hasAttentionMark,
            attentionSources,
            buildAttentionLabel(attentionSources),
            hasAttentionMark,
            buildSpecialSummary(note, merchantRemark),
            row.itemStatus(),
            row.receiptStatus(),
            row.receiptUrl(),
            row.receiptNote(),
            row.referenceImageUrl(),
            row.latitude(),
            row.longitude()
        );
    }

    private LocalDate resolveServeDateOrToday(String serveDate) {
        if (serveDate == null) {
            return LocalDate.now();
        }
        String normalized = serveDate.trim();
        if (normalized.isEmpty()
            || "undefined".equalsIgnoreCase(normalized)
            || "null".equalsIgnoreCase(normalized)) {
            return LocalDate.now();
        }
        return LocalDate.parse(normalized);
    }

    private void ensureRiderQueueMaterialized(Long riderId, LocalDate serveDate) {
        if (riderId == null || serveDate == null) {
            return;
        }
        List<RiderAssignmentRow> assignments = jdbcTemplate.query("""
            SELECT
                da.meal_slot_order_id,
                da.rider_profile_id,
                da.area_code,
                da.status,
                da.sequence_number
            FROM dispatch_assignments da
            JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            WHERE da.rider_profile_id = ?
              AND doo.serve_date = ?
            """, (rs, rowNum) -> new RiderAssignmentRow(
            rs.getLong("meal_slot_order_id"),
            rs.getObject("rider_profile_id") == null ? null : rs.getLong("rider_profile_id"),
            rs.getString("area_code"),
            rs.getString("status"),
            rs.getObject("sequence_number") == null ? null : rs.getInt("sequence_number")
        ), riderId, serveDate);
        // 同一批次在一次刷新里只刷一次：旧实现按订单循环调用 refreshRiderBatchState，
        // 一个 15 单的批次单次刷新要做 15 遍全批更新，是写放大与锁竞争的主要来源。
        Set<Long> touchedBatchIds = new LinkedHashSet<>();
        for (RiderAssignmentRow assignment : assignments) {
            if (assignment.riderProfileId() == null) {
                continue;
            }
            touchedBatchIds.addAll(ensureQueueBatchItem(
                assignment.orderId(),
                assignment.riderProfileId(),
                assignment.areaCode(),
                assignment.status(),
                assignment.sequenceNumber()
            ));
        }
        for (Long touchedBatchId : touchedBatchIds) {
            if (touchedBatchId != null && touchedBatchId > 0L) {
                runWithLockRetry(() -> refreshRiderBatchState(touchedBatchId));
            }
        }
    }

    /**
     * 物化跑在读路径上，锁冲突（死锁/锁等待超时）不该以 500 抛给骑手。
     * 物化本身幂等，冲突时重跑最多 2 次，退避 30ms / 60ms。
     */
    private void runWithLockRetry(Runnable action) {
        int attempt = 0;
        while (true) {
            try {
                action.run();
                return;
            } catch (ConcurrencyFailureException ex) {
                attempt++;
                if (attempt > 2) {
                    throw ex;
                }
                try {
                    Thread.sleep(30L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    private List<Long> ensureQueueBatchItem(long orderId, long riderProfileId, String areaCode, String assignmentStatus, Number assignmentSequenceNumber) {
        MealSlotContext orderContext = loadMealSlotContext(orderId);
        long batchId = ensureQueueBatch(orderId, riderProfileId, areaCode, orderContext.serveDate(), orderContext.mealPeriod());
        String desiredItemStatus = mapBatchItemStatus(assignmentStatus);
        List<BatchItemSnapshot> existingItems = jdbcTemplate.query("""
            SELECT id, batch_id, current_sequence, item_status
            FROM dispatch_batch_items
            WHERE meal_slot_order_id = ?
            ORDER BY id ASC
            LIMIT 1
            """, (rs, rowNum) -> new BatchItemSnapshot(
            rs.getLong("id"),
            rs.getLong("batch_id"),
            rs.getInt("current_sequence"),
            rs.getString("item_status")
        ), orderId);
        Long previousBatchId = null;
        if (existingItems.isEmpty()) {
            // 幂等插入：并发下若该订单已有批次项，则改为更新，避免撞 uk_dispatch_batch_items_order 唯一键。
            // 序号统一用 batch 内 MAX+1（nextBatchSequence），不直接复用 dispatch_assignments.sequence_number，
            // 防止区域序号与批次序号不一致时撞 uk_dispatch_batch_items_batch_sequence 唯一键，引发"数据冲突"。
            int sequence = nextBatchSequence(batchId);
            jdbcTemplate.update("""
                INSERT INTO dispatch_batch_items (
                    batch_id,
                    meal_slot_order_id,
                    current_sequence,
                    suggested_sequence,
                    item_status,
                    manually_adjusted
                ) VALUES (?, ?, ?, ?, ?, FALSE)
                ON DUPLICATE KEY UPDATE
                    batch_id = VALUES(batch_id),
                    current_sequence = VALUES(current_sequence),
                    suggested_sequence = VALUES(suggested_sequence),
                    item_status = VALUES(item_status)
                """, batchId, orderId, sequence, sequence, desiredItemStatus);
        } else {
            BatchItemSnapshot existing = existingItems.get(0);
            long existingBatchId = existing.batchId();
            boolean movingBatch = existingBatchId != batchId;
            if (movingBatch) {
                previousBatchId = existingBatchId;
            }
            // 跨批次迁移时用目标批次的 MAX+1，避免与目标批次已有序号冲突；
            // 已在正确批次内时保持原序号不动，只同步状态。
            int finalSequence = movingBatch ? nextBatchSequence(batchId) : existing.currentSequence();
            // item_status = CURRENT 是“队列当前项”，由 refreshRiderBatchState 统一维护；
            // 派单侧只表达 PENDING/DELIVERED/DEFERRED。若在这里把 CURRENT 降回 PENDING，
            // 紧随其后的 refresh 又会把它升回 CURRENT —— 每次刷新白写两行，故跳过这种降级。
            boolean statusNeedsSync = !desiredItemStatus.equals(existing.itemStatus())
                && !("CURRENT".equals(existing.itemStatus()) && "PENDING".equals(desiredItemStatus));
            if (movingBatch || statusNeedsSync) {
                jdbcTemplate.update("""
                    UPDATE dispatch_batch_items
                    SET batch_id = ?,
                        current_sequence = ?,
                        suggested_sequence = ?,
                        item_status = ?
                    WHERE id = ?
                    """, batchId, finalSequence, finalSequence, desiredItemStatus, existing.id());
            }
        }
        // 刷新交给调用方去重后统一执行，避免同一批次在一次刷新内被反复刷。
        List<Long> touchedBatchIds = new ArrayList<>();
        touchedBatchIds.add(batchId);
        if (previousBatchId != null && previousBatchId.longValue() != batchId) {
            touchedBatchIds.add(previousBatchId);
        }
        return touchedBatchIds;
    }

    private long ensureQueueBatch(long orderId, long riderProfileId, String areaCode, LocalDate serveDate, String mealPeriod) {
        List<Long> batchIds = jdbcTemplate.query("""
            SELECT id
            FROM dispatch_batches
            WHERE serve_date = ?
              AND meal_period = ?
              AND rider_profile_id = ?
              AND area_code <=> ?
            ORDER BY id ASC
            LIMIT 1
            """, (rs, rowNum) -> rs.getLong("id"), serveDate, mealPeriod, riderProfileId, areaCode);
        if (!batchIds.isEmpty()) {
            return batchIds.get(0);
        }
        // 幂等创建批次：并发物化时可能同时插入同一批次，INSERT IGNORE 避免撞 uk_dispatch_batches_scope 唯一键。
        jdbcTemplate.update(
            """
                INSERT IGNORE INTO dispatch_batches (
                    serve_date,
                    meal_period,
                    rider_profile_id,
                    area_code,
                    batch_status,
                    total_count,
                    delivered_count,
                    current_sequence
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            serveDate,
            mealPeriod,
            riderProfileId,
            areaCode,
            "READY",
            0,
            0,
            1
        );
        // 重新查询已存在的批次（并发下可能由其他线程刚创建），避免返回空 id。
        List<Long> refreshedIds = jdbcTemplate.query("""
            SELECT id
            FROM dispatch_batches
            WHERE serve_date = ?
              AND meal_period = ?
              AND rider_profile_id = ?
              AND area_code <=> ?
            ORDER BY id ASC
            LIMIT 1
            """, (rs, rowNum) -> rs.getLong("id"), serveDate, mealPeriod, riderProfileId, areaCode);
        if (!refreshedIds.isEmpty()) {
            jdbcTemplate.update(
                "UPDATE dispatch_assignments SET rider_profile_id = ?, area_code = ? WHERE meal_slot_order_id = ?",
                riderProfileId,
                areaCode,
                orderId
            );
            return refreshedIds.get(0);
        }
        return 0L;
    }

    private int nextBatchSequence(long batchId) {
        Integer sequence = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(current_sequence), 0) + 1 FROM dispatch_batch_items WHERE batch_id = ?",
            Integer.class,
            batchId
        );
        return sequence == null ? 1 : sequence;
    }

    private String mapBatchItemStatus(String assignmentStatus) {
        if ("DELIVERED".equals(assignmentStatus)) {
            return "DELIVERED";
        }
        if ("DEFERRED".equals(assignmentStatus)) {
            return "DEFERRED";
        }
        return "PENDING";
    }

    private RiderBatchItemContext requireRiderBatchItem(Long riderId, long batchItemId) {
        List<RiderBatchItemContext> rows = jdbcTemplate.query("""
            SELECT dbi.id, dbi.batch_id, dbi.current_sequence, dbi.item_status
            FROM dispatch_batch_items dbi
            JOIN dispatch_batches db ON db.id = dbi.batch_id
            JOIN rider_profiles rp ON rp.id = db.rider_profile_id
            WHERE db.rider_profile_id = ? AND dbi.id = ?
            """, (rs, rowNum) -> new RiderBatchItemContext(
            rs.getLong("id"),
            rs.getLong("batch_id"),
            rs.getInt("current_sequence"),
            rs.getString("item_status")
        ), riderId, batchItemId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RIDER_TASK_NOT_FOUND, "未找到对应配送队列项");
        }
        return rows.get(0);
    }

    private void refreshRiderBatchState(long batchId) {
        // 一次读出整批明细 → 内存算期望状态 → 只对“状态确实变了”的行按主键更新。
        // 旧实现用 `WHERE batch_id = ? AND item_status = 'CURRENT'` 这种非唯一前缀的范围更新，
        // InnoDB 会对整批索引区间加 X 记录锁 + 间隙锁，两个并发请求即使改的是**不同订单**
        // 也会争同一段锁区间，再叠加同一请求内“先锁单行、后锁整批”的顺序反转即成环死锁
        // （2026-09-09 / 09-10 骑手刷不出数据的生产事故根因）。
        List<BatchItemSnapshot> items = jdbcTemplate.query("""
            SELECT id, batch_id, current_sequence, item_status
            FROM dispatch_batch_items
            WHERE batch_id = ?
            ORDER BY current_sequence ASC, id ASC
            """, (rs, rowNum) -> new BatchItemSnapshot(
            rs.getLong("id"),
            rs.getLong("batch_id"),
            rs.getInt("current_sequence"),
            rs.getString("item_status")
        ), batchId);
        // 语义与旧实现逐行等价：CURRENT 全部降为 PENDING，再把序号最小的 PENDING 升为 CURRENT，
        // DELIVERED / DEFERRED 保持不变。
        Map<Long, String> desiredStatus = new LinkedHashMap<>();
        Long nextCurrentId = null;
        for (BatchItemSnapshot item : items) {
            String currentStatus = item.itemStatus();
            desiredStatus.put(item.id(), "CURRENT".equals(currentStatus) ? "PENDING" : currentStatus);
        }
        for (BatchItemSnapshot item : items) {
            if ("PENDING".equals(desiredStatus.get(item.id()))) {
                nextCurrentId = item.id();
                break;
            }
        }
        if (nextCurrentId != null) {
            desiredStatus.put(nextCurrentId, "CURRENT");
        }
        for (BatchItemSnapshot item : items) {
            String wantedStatus = desiredStatus.get(item.id());
            if (wantedStatus != null && !wantedStatus.equals(item.itemStatus())) {
                jdbcTemplate.update(
                    "UPDATE dispatch_batch_items SET item_status = ? WHERE id = ?",
                    wantedStatus,
                    item.id()
                );
            }
        }
        Integer deliveredCount = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(mso.quantity), 0)
                FROM dispatch_batch_items dbi
                JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
                WHERE dbi.batch_id = ?
                  AND dbi.item_status = 'DELIVERED'
                """,
            Integer.class,
            batchId
        );
        Integer totalCount = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(mso.quantity), 0)
                FROM dispatch_batch_items dbi
                JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
                WHERE dbi.batch_id = ?
                """,
            Integer.class,
            batchId
        );
        Integer nextSequence = jdbcTemplate.queryForObject(
            "SELECT MIN(current_sequence) FROM dispatch_batch_items WHERE batch_id = ? AND item_status = 'CURRENT'",
            Integer.class,
            batchId
        );
        String batchStatus;
        if (totalCount != null && deliveredCount != null && deliveredCount.intValue() >= totalCount.intValue()) {
            batchStatus = "FINISHED";
        } else if (deliveredCount != null && deliveredCount > 0) {
            batchStatus = "PARTIALLY_DONE";
        } else {
            batchStatus = "IN_PROGRESS";
        }
        int nextTotalCount = totalCount == null ? 0 : totalCount;
        int nextDeliveredCount = deliveredCount == null ? 0 : deliveredCount;
        int nextCurrentSequence = nextSequence == null ? 0 : nextSequence;
        // 批头仅在数值真的变化时才写：稳态（骑手反复刷新但没人完成订单）完全不写，
        // 也就不会再每 8 秒去占一次批次头行的锁。
        List<BatchHeaderSnapshot> headers = jdbcTemplate.query("""
            SELECT total_count, delivered_count, current_sequence, batch_status
            FROM dispatch_batches
            WHERE id = ?
            """, (rs, rowNum) -> new BatchHeaderSnapshot(
            rs.getInt("total_count"),
            rs.getInt("delivered_count"),
            rs.getInt("current_sequence"),
            rs.getString("batch_status")
        ), batchId);
        BatchHeaderSnapshot header = headers.isEmpty() ? null : headers.get(0);
        if (header == null
            || header.totalCount() != nextTotalCount
            || header.deliveredCount() != nextDeliveredCount
            || header.currentSequence() != nextCurrentSequence
            || !batchStatus.equals(header.batchStatus())) {
            jdbcTemplate.update("""
                UPDATE dispatch_batches
                SET total_count = ?,
                    delivered_count = ?,
                    current_sequence = ?,
                    batch_status = ?
                WHERE id = ?
                """,
                nextTotalCount,
                nextDeliveredCount,
                nextCurrentSequence,
                batchStatus,
                batchId
            );
        }
        syncDispatchAssignmentsFromBatch(batchId);
    }

    private void syncDispatchAssignmentsFromBatch(long batchId) {
        // 与批头同理：先读现值，只回写真的变化的派单行（旧实现无脑 UPDATE 每条明细）。
        List<BatchAssignmentSyncRow> rows = jdbcTemplate.query("""
                SELECT dbi.meal_slot_order_id AS meal_slot_order_id,
                       dbi.current_sequence AS current_sequence,
                       dbi.item_status AS item_status,
                       da.id AS assignment_id,
                       da.sequence_number AS assignment_sequence,
                       da.status AS assignment_status
                FROM dispatch_batch_items dbi
                LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id = dbi.meal_slot_order_id
                WHERE dbi.batch_id = ?
                ORDER BY dbi.current_sequence ASC, dbi.id ASC
                """,
            (rs, rowNum) -> new BatchAssignmentSyncRow(
                rs.getLong("meal_slot_order_id"),
                rs.getInt("current_sequence"),
                rs.getString("item_status"),
                rs.getObject("assignment_id") == null ? null : rs.getLong("assignment_id"),
                rs.getObject("assignment_sequence") == null ? null : rs.getInt("assignment_sequence"),
                rs.getString("assignment_status")
            ), batchId);
        for (BatchAssignmentSyncRow row : rows) {
            if (row.assignmentId() == null) {
                continue;
            }
            String wantedStatus = mapAssignmentStatus(row.itemStatus());
            if (row.assignmentSequence() != null
                && row.assignmentSequence() == row.currentSequence()
                && wantedStatus.equals(row.assignmentStatus())) {
                continue;
            }
            jdbcTemplate.update("""
                    UPDATE dispatch_assignments
                    SET sequence_number = ?,
                        status = ?
                    WHERE meal_slot_order_id = ?
                """,
                row.currentSequence(),
                wantedStatus,
                row.mealSlotOrderId()
            );
        }
    }

    private String mapAssignmentStatus(String itemStatus) {
        if ("DELIVERED".equals(itemStatus)) {
            return "DELIVERED";
        }
        if ("DEFERRED".equals(itemStatus)) {
            return "DEFERRED";
        }
        return "DISPATCHING";
    }

    private void publishRiderEvent(String eventType, String riderName, Object orderId) {
        realtimeAudienceModule.publishRiderEvent(eventType, riderName, orderId);
    }

    private String resolveRiderName(Long riderId) {
        if (riderId == null) {
            return "";
        }
        List<String> names = jdbcTemplate.query(
            "SELECT rider_name FROM rider_profiles WHERE id = ?",
            (rs, rowNum) -> rs.getString("rider_name"),
            riderId
        );
        return names.isEmpty() ? "" : names.get(0);
    }

    private String buildSpecialSummary(String userNote, String adminNote) {
        List<String> parts = new ArrayList<>();
        if (!normalizeSpecialValue(userNote).isEmpty()) {
            parts.add("用户备注");
        }
        if (!normalizeSpecialValue(adminNote).isEmpty()) {
            parts.add("商家备注");
        }
        return String.join(" / ", parts);
    }

    private List<String> buildAttentionSources(String userNote, String adminNote) {
        List<String> sources = new ArrayList<>();
        if (!normalizeSpecialValue(userNote).isEmpty()) {
            sources.add("USER_NOTE");
        }
        if (!normalizeSpecialValue(adminNote).isEmpty()) {
            sources.add("MERCHANT_NOTE");
        }
        return List.copyOf(sources);
    }

    private String buildAttentionLabel(List<String> attentionSources) {
        return attentionSources.isEmpty() ? "" : "有备注";
    }

    private String normalizeSpecialValue(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return "-".equals(normalized) ? "" : normalized;
    }

    private Map<Long, OrderNoteProjection> loadOrderNoteProjections(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
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
        Map<Long, RiderOrderNoteAccumulator> accumulators = new LinkedHashMap<>();
        for (OrderNoteRow row : rows) {
            accumulators.computeIfAbsent(row.orderId(), ignored -> new RiderOrderNoteAccumulator())
                .add(row.noteType(), row.content());
        }
        Map<Long, OrderNoteProjection> projections = new LinkedHashMap<>();
        for (Long orderId : orderIds) {
            RiderOrderNoteAccumulator accumulator = accumulators.get(orderId);
            if (accumulator != null) {
                projections.put(orderId, accumulator.toProjection());
            }
        }
        return projections;
    }

    /**
     * 备注展示 = 快照条目（长期 + 本单一次）在前，订单列值兜底追加在后，去重后逗号拼接。
     * 骑手端沿用既有语义：用户备注为空时展示 `-`。
     */
    private String resolveProjectedUserNote(OrderNoteProjection projection, String legacyValue) {
        List<String> parts = projection == null
            ? new ArrayList<>()
            : OrderNoteTexts.newParts(projection.userNotes());
        OrderNoteTexts.addPart(parts, legacyValue);
        String joined = OrderNoteTexts.join(parts);
        return joined.isEmpty() ? "-" : joined;
    }

    private String resolveProjectedAdminNote(OrderNoteProjection projection, String legacyValue) {
        List<String> parts = projection == null
            ? new ArrayList<>()
            : OrderNoteTexts.newParts(projection.merchantNotes());
        OrderNoteTexts.addPart(parts, legacyValue);
        return OrderNoteTexts.join(parts);
    }

    private MealSlotContext loadMealSlotContext(long mealSlotOrderId) {
        return jdbcTemplate.query("""
                SELECT do.serve_date, COALESCE(mso.delivery_meal_period, mso.meal_period) AS meal_period
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE mso.id = ?
                """,
            ps -> ps.setLong(1, mealSlotOrderId),
            rs -> {
                if (!rs.next()) {
                    throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "未找到对应订单");
                }
                return new MealSlotContext(
                    rs.getObject("serve_date", LocalDate.class),
                    rs.getString("meal_period")
                );
            }
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

    private record RiderBatchItemContext(long batchItemId, long batchId, int currentSequence, String itemStatus) {}

    private record RiderQueueRow(
        long batchItemId,
        long batchId,
        long mealSlotOrderId,
        long addressId,
        int currentSequence,
        String customerName,
        String customerPhone,
        String deliveryAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        String productionMealPeriod,
        String deliveryMealPeriod,
        String mealName,
        int quantity,
        String note,
        String merchantRemark,
        String itemStatus,
        String receiptStatus,
        String receiptUrl,
        String receiptNote,
        String referenceImageUrl
    ) {}

    private record OrderNoteProjection(List<String> userNotes, List<String> merchantNotes) {}

    private static final class RiderOrderNoteAccumulator {
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
            return new OrderNoteProjection(List.copyOf(userNotes), List.copyOf(merchantNotes));
        }
    }

    private record OrderNoteRow(long orderId, String noteType, String content) {}

    private record RiderAssignmentRow(long orderId, Long riderProfileId, String areaCode, String status, Number sequenceNumber) {}

    private record MealSlotContext(LocalDate serveDate, String mealPeriod) {}

    private record BatchItemSnapshot(long id, long batchId, int currentSequence, String itemStatus) {}

    private record BatchItemSequenceRow(long id, long batchId, int currentSequence) {}

    private record BatchHeaderSnapshot(int totalCount, int deliveredCount, int currentSequence, String batchStatus) {}

    private record BatchAssignmentSyncRow(
        long mealSlotOrderId,
        int currentSequence,
        String itemStatus,
        Long assignmentId,
        Integer assignmentSequence,
        String assignmentStatus
    ) {}

    private record DeliveryExceptionOrderInfo(long riderProfileId, String customerPhone, String deliveryAddress) {}
}
