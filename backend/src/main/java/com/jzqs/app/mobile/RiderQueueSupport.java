package com.jzqs.app.mobile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeEvent;
import com.jzqs.app.common.realtime.TransactionalRealtimePublisher;
import com.jzqs.app.mobile.api.RiderDeliveryExceptionReportResponse;
import com.jzqs.app.mobile.api.RiderQueueItemActionResponse;
import com.jzqs.app.mobile.api.RiderQueueItemResponse;
import com.jzqs.app.mobile.api.RiderQueueReorderResponse;
import com.jzqs.app.mobile.api.RiderBatchSummaryResponse;
import com.jzqs.app.mobile.api.RiderTaskItemResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
class RiderQueueSupport {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionalRealtimePublisher realtimeEventPublisher;

    RiderQueueSupport(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, TransactionalRealtimePublisher realtimeEventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    PageResponse<RiderTaskItemResponse> riderTasks(String riderName) {
        LocalDate today = LocalDate.now();
        List<RiderTaskItemResponse> items = jdbcTemplate.query("""
            SELECT
                da.id AS dispatch_id,
                mso.id AS meal_slot_order_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                ca.address_line AS delivery_address,
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
            WHERE da.rider_name = ?
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
        ), riderName, today);
        return PageResponse.of(items, 1, 20, items.size());
    }

    RiderBatchSummaryResponse riderSummary(String riderName, String serveDate) {
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
            WHERE rp.rider_name = ?
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
        ), riderName, targetDate);
        RiderBatchSummaryResponse.BatchCardResponse lunch = cards.stream().filter(item -> "LUNCH".equals(item.mealPeriod())).findFirst().orElse(null);
        RiderBatchSummaryResponse.BatchCardResponse dinner = cards.stream().filter(item -> "DINNER".equals(item.mealPeriod())).findFirst().orElse(null);
        int totalCount = cards.stream().mapToInt(RiderBatchSummaryResponse.BatchCardResponse::totalCount).sum();
        int deliveredCount = cards.stream().mapToInt(RiderBatchSummaryResponse.BatchCardResponse::deliveredCount).sum();
        return new RiderBatchSummaryResponse(
            riderName,
            totalCount,
            deliveredCount,
            Math.max(totalCount - deliveredCount, 0),
            lunch,
            dinner
        );
    }

    PageResponse<RiderTaskItemResponse> riderCompletedToday(String riderName) {
        LocalDate today = LocalDate.now();
        List<RiderTaskItemResponse> items = jdbcTemplate.query("""
            SELECT
                da.id AS dispatch_id,
                mso.id AS meal_slot_order_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                ca.address_line AS delivery_address,
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
            WHERE da.rider_name = ?
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
        ), riderName, today, today);
        return PageResponse.of(items, 1, 20, items.size());
    }

    RiderDeliveryExceptionReportResponse reportDeliveryException(
        long mealSlotOrderId,
        String riderName,
        String exceptionType,
        String exceptionNote,
        List<String> exceptionImages
    ) {
        DeliveryExceptionOrderInfo orderInfo = jdbcTemplate.query("""
            SELECT
                rp.id AS rider_profile_id,
                c.phone AS customer_phone,
                ca.address_line AS delivery_address
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            CROSS JOIN rider_profiles rp
            WHERE mso.id = ? AND rp.rider_name = ?
            """,
            ps -> {
                ps.setLong(1, mealSlotOrderId);
                ps.setString(2, riderName);
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
            riderName,
            exceptionType,
            exceptionNote,
            orderInfo.customerPhone(),
            orderInfo.deliveryAddress(),
            imagesJson
        );
        publishRiderEvent("dispatch.exception.changed", riderName, mealSlotOrderId);
        return new RiderDeliveryExceptionReportResponse(exceptionId, "REPORTED", "异常已上报，请等待处理");
    }

    PageResponse<RiderQueueItemResponse> riderQueue(String riderName, String serveDate) {
        LocalDate targetDate = resolveServeDateOrToday(serveDate);
        ensureRiderQueueMaterialized(riderName, targetDate);
        List<RiderQueueRow> rows = jdbcTemplate.query("""
            SELECT
                COALESCE(dbi.id, 0) AS batch_item_id,
                COALESCE(db.id, 0) AS batch_id,
                mso.id AS meal_slot_order_id,
                mso.address_id AS address_id,
                COALESCE(NULLIF(dbi.current_sequence, 0), NULLIF(da.sequence_number, 0), 0) AS current_sequence,
                c.name AS customer_name,
                c.phone AS customer_phone,
                ca.address_line AS delivery_address,
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
            WHERE da.rider_name = ?
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
            rs.getString("production_meal_period"),
            rs.getString("delivery_meal_period"),
            rs.getString("meal_name"),
            rs.getInt("quantity"),
            rs.getString("note"),
            rs.getString("merchant_remark"),
            rs.getString("item_status"),
            rs.getString("receipt_status"),
            rs.getString("receipt_url"),
            rs.getString("receipt_note")
        ), riderName, targetDate);
        Map<Long, OrderNoteProjection> projections = loadOrderNoteProjections(rows.stream().map(RiderQueueRow::mealSlotOrderId).toList());
        List<RiderQueueItemResponse> items = rows.stream()
            .map(row -> buildRiderQueueItemResponse(row, projections.get(row.mealSlotOrderId())))
            .toList();
        return PageResponse.of(items, 1, 50, items.size());
    }

    RiderQueueItemResponse riderQueueItem(long queueItemId, String riderName, String serveDate, Long mealSlotOrderId) {
        LocalDate targetDate = resolveServeDateOrToday(serveDate);
        ensureRiderQueueMaterialized(riderName, targetDate);
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
                ca.address_line AS delivery_address,
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
              AND da.rider_name = ?
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
                ca.address_line AS delivery_address,
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
              AND da.rider_name = ?
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
            rs.getString("production_meal_period"),
            rs.getString("delivery_meal_period"),
            rs.getString("meal_name"),
            rs.getInt("quantity"),
            rs.getString("note"),
            rs.getString("merchant_remark"),
            rs.getString("item_status"),
            rs.getString("receipt_status"),
            rs.getString("receipt_url"),
            rs.getString("receipt_note")
        ), shouldUseMealSlotOrderFallback ? resolvedMealSlotOrderId : queueItemId, riderName, targetDate);
        if (results.isEmpty()) {
            return null;
        }
        RiderQueueRow row = results.get(0);
        OrderNoteProjection projection = loadOrderNoteProjections(List.of(row.mealSlotOrderId())).get(row.mealSlotOrderId());
        return buildRiderQueueItemResponse(row, projection);
    }

    RiderQueueReorderResponse reorderRiderQueue(String riderName, List<Long> batchItemIds) {
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
                """, sequence++, riderName, batchItemId);
        }
        syncDispatchAssignmentsFromBatch(batchId);
        publishRiderEvent("dispatch.queue.changed", riderName, batchItemIds.get(0));
        return new RiderQueueReorderResponse(batchItemIds.size(), "REORDERED");
    }

    RiderQueueItemActionResponse deferRiderQueueItem(String riderName, long batchItemId) {
        RiderBatchItemContext context = requireRiderBatchItem(riderName, batchItemId);
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
            """, riderName, context.batchId(), context.currentSequence());
        jdbcTemplate.update("""
            UPDATE dispatch_batch_items
            SET current_sequence = ?,
                item_status = 'DEFERRED',
                manually_adjusted = TRUE,
                reordered_by = ?,
                reordered_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, lastSequence, riderName, batchItemId);
        refreshRiderBatchState(context.batchId());
        publishRiderEvent("dispatch.queue.changed", riderName, batchItemId);
        return new RiderQueueItemActionResponse(batchItemId, "DEFERRED", "DEFERRED");
    }

    RiderQueueItemActionResponse resumeRiderQueueItem(String riderName, long batchItemId) {
        RiderBatchItemContext context = requireRiderBatchItem(riderName, batchItemId);
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
            """, riderName, batchItemId);
        refreshRiderBatchState(context.batchId());
        String finalStatus = jdbcTemplate.queryForObject(
            "SELECT item_status FROM dispatch_batch_items WHERE id = ?",
            String.class,
            batchItemId
        );
        publishRiderEvent("dispatch.queue.changed", riderName, batchItemId);
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
            row.receiptNote()
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

    private void ensureRiderQueueMaterialized(String riderName, LocalDate serveDate) {
        if (riderName == null || riderName.isBlank() || serveDate == null) {
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
            WHERE da.rider_name = ?
              AND doo.serve_date = ?
            """, (rs, rowNum) -> new RiderAssignmentRow(
            rs.getLong("meal_slot_order_id"),
            rs.getObject("rider_profile_id") == null ? null : rs.getLong("rider_profile_id"),
            rs.getString("area_code"),
            rs.getString("status"),
            rs.getObject("sequence_number") == null ? null : rs.getInt("sequence_number")
        ), riderName, serveDate);
        for (RiderAssignmentRow assignment : assignments) {
            if (assignment.riderProfileId() == null) {
                continue;
            }
            ensureQueueBatchItem(
                assignment.orderId(),
                assignment.riderProfileId(),
                assignment.areaCode(),
                assignment.status(),
                assignment.sequenceNumber()
            );
        }
    }

    private void ensureQueueBatchItem(long orderId, long riderProfileId, String areaCode, String assignmentStatus, Number assignmentSequenceNumber) {
        MealSlotContext orderContext = loadMealSlotContext(orderId);
        long batchId = ensureQueueBatch(orderId, riderProfileId, areaCode, orderContext.serveDate(), orderContext.mealPeriod());
        int desiredSequence = assignmentSequenceNumber != null && assignmentSequenceNumber.intValue() > 0
            ? assignmentSequenceNumber.intValue()
            : nextBatchSequence(batchId);
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
            jdbcTemplate.update("""
                INSERT INTO dispatch_batch_items (
                    batch_id,
                    meal_slot_order_id,
                    current_sequence,
                    suggested_sequence,
                    item_status,
                    manually_adjusted
                ) VALUES (?, ?, ?, ?, ?, FALSE)
                """, batchId, orderId, desiredSequence, desiredSequence, desiredItemStatus);
        } else {
            BatchItemSnapshot existing = existingItems.get(0);
            long existingBatchId = existing.batchId();
            int finalSequence = assignmentSequenceNumber != null && assignmentSequenceNumber.intValue() > 0
                ? assignmentSequenceNumber.intValue()
                : existing.currentSequence();
            if (existingBatchId != batchId) {
                previousBatchId = existingBatchId;
            }
            if (existingBatchId != batchId
                || existing.currentSequence() != finalSequence
                || !desiredItemStatus.equals(existing.itemStatus())) {
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
        refreshRiderBatchState(batchId);
        if (previousBatchId != null && previousBatchId.longValue() != batchId) {
            refreshRiderBatchState(previousBatchId);
        }
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
        long batchId = insertAndReturnId(
            """
                INSERT INTO dispatch_batches (
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
        jdbcTemplate.update(
            "UPDATE dispatch_assignments SET rider_profile_id = ?, area_code = ? WHERE meal_slot_order_id = ?",
            riderProfileId,
            areaCode,
            orderId
        );
        return batchId;
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

    private RiderBatchItemContext requireRiderBatchItem(String riderName, long batchItemId) {
        List<RiderBatchItemContext> rows = jdbcTemplate.query("""
            SELECT dbi.id, dbi.batch_id, dbi.current_sequence, dbi.item_status
            FROM dispatch_batch_items dbi
            JOIN dispatch_batches db ON db.id = dbi.batch_id
            JOIN rider_profiles rp ON rp.id = db.rider_profile_id
            WHERE rp.rider_name = ? AND dbi.id = ?
            """, (rs, rowNum) -> new RiderBatchItemContext(
            rs.getLong("id"),
            rs.getLong("batch_id"),
            rs.getInt("current_sequence"),
            rs.getString("item_status")
        ), riderName, batchItemId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RIDER_TASK_NOT_FOUND, "未找到对应配送队列项");
        }
        return rows.get(0);
    }

    private void refreshRiderBatchState(long batchId) {
        jdbcTemplate.update("UPDATE dispatch_batch_items SET item_status = 'PENDING' WHERE batch_id = ? AND item_status = 'CURRENT'", batchId);
        List<Long> currentIds = jdbcTemplate.query("""
            SELECT id
            FROM dispatch_batch_items
            WHERE batch_id = ? AND item_status = 'PENDING'
            ORDER BY current_sequence ASC
            LIMIT 1
            """, (rs, rowNum) -> rs.getLong("id"), batchId);
        if (!currentIds.isEmpty()) {
            jdbcTemplate.update("UPDATE dispatch_batch_items SET item_status = 'CURRENT' WHERE id = ?", currentIds.get(0));
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
        jdbcTemplate.update("""
            UPDATE dispatch_batches
            SET delivered_count = ?,
                current_sequence = ?,
                batch_status = ?
            WHERE id = ?
            """,
            deliveredCount == null ? 0 : deliveredCount,
            nextSequence == null ? 0 : nextSequence,
            batchStatus,
            batchId
        );
        syncDispatchAssignmentsFromBatch(batchId);
    }

    private void syncDispatchAssignmentsFromBatch(long batchId) {
        jdbcTemplate.query("""
                SELECT meal_slot_order_id, current_sequence, item_status
                FROM dispatch_batch_items
                WHERE batch_id = ?
                ORDER BY current_sequence ASC, id ASC
                """,
            ps -> ps.setLong(1, batchId),
            rs -> {
                while (rs.next()) {
                    jdbcTemplate.update("""
                            UPDATE dispatch_assignments
                            SET sequence_number = ?,
                                status = ?
                            WHERE meal_slot_order_id = ?
                        """,
                        rs.getInt("current_sequence"),
                        mapAssignmentStatus(rs.getString("item_status")),
                        rs.getLong("meal_slot_order_id")
                    );
                }
                return null;
            }
        );
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
        RealtimeEvent.Builder builder = RealtimeEvent.builder(eventType)
            .audience("admin")
            .audience("rider:all");
        if (riderName != null && !riderName.isBlank()) {
            builder.audience("rider:name:" + riderName.trim()).payload("riderName", riderName.trim());
        }
        if (orderId != null) {
            builder.payload("orderId", orderId);
        }
        realtimeEventPublisher.publish(builder.build());
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
        return attentionSources.isEmpty() ? "" : "需留意";
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

    private String resolveProjectedUserNote(OrderNoteProjection projection, String legacyValue) {
        if (projection != null && projection.hasOrderNotes()) {
            return projection.userNote().isBlank() ? "-" : projection.userNote();
        }
        String normalized = normalizeSpecialValue(legacyValue);
        return normalized.isBlank() ? "-" : normalized;
    }

    private String resolveProjectedAdminNote(OrderNoteProjection projection, String legacyValue) {
        if (projection != null && projection.hasOrderNotes()) {
            return projection.adminNote();
        }
        return normalizeSpecialValue(legacyValue);
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
        String productionMealPeriod,
        String deliveryMealPeriod,
        String mealName,
        int quantity,
        String note,
        String merchantRemark,
        String itemStatus,
        String receiptStatus,
        String receiptUrl,
        String receiptNote
    ) {}

    private record OrderNoteProjection(String userNote, String adminNote, boolean hasOrderNotes) {}

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
            return new OrderNoteProjection(String.join(" / ", userNotes), String.join(" / ", merchantNotes), true);
        }
    }

    private record OrderNoteRow(long orderId, String noteType, String content) {}

    private record RiderAssignmentRow(long orderId, Long riderProfileId, String areaCode, String status, Number sequenceNumber) {}

    private record MealSlotContext(LocalDate serveDate, String mealPeriod) {}

    private record BatchItemSnapshot(long id, long batchId, int currentSequence, String itemStatus) {}

    private record BatchItemSequenceRow(long id, long batchId, int currentSequence) {}

    private record DeliveryExceptionOrderInfo(long riderProfileId, String customerPhone, String deliveryAddress) {}
}
