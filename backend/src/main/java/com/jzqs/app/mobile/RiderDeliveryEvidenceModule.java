package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.delivery.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.delivery.service.DeliveryService;
import com.jzqs.app.mobile.api.RiderAddressReferenceBatchSaveResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceReplaceResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceResponse;
import com.jzqs.app.mobile.api.RiderBatchAddressReferenceRequest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class RiderDeliveryEvidenceModule {
    private final JdbcTemplate jdbcTemplate;
    private final DeliveryService deliveryService;
    private final RiderQueueSupport riderQueueSupport;
    private final RiderReceiptStorageSupport riderReceiptStorageSupport;
    private final DeliverySubscriptionModule deliverySubscriptionModule;

    RiderDeliveryEvidenceModule(
        JdbcTemplate jdbcTemplate,
        DeliveryService deliveryService,
        RiderQueueSupport riderQueueSupport,
        RiderReceiptStorageSupport riderReceiptStorageSupport,
        DeliverySubscriptionModule deliverySubscriptionModule
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.deliveryService = deliveryService;
        this.riderQueueSupport = riderQueueSupport;
        this.riderReceiptStorageSupport = riderReceiptStorageSupport;
        this.deliverySubscriptionModule = deliverySubscriptionModule;
    }

    RiderAddressReferenceResponse riderAddressReference(String riderName, long addressId) {
        requireRiderName(riderName);
        if (addressId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "地址不能为空");
        }
        List<RiderAddressReferenceResponse> results = jdbcTemplate.query(
            """
                SELECT customer_address_id, reference_image_url
                FROM address_reference_images
                WHERE customer_address_id = ?
                """,
            (rs, rowNum) -> new RiderAddressReferenceResponse(
                rs.getLong("customer_address_id"),
                rs.getString("reference_image_url")
            ),
            addressId
        );
        if (results.isEmpty()) {
            return new RiderAddressReferenceResponse(addressId, "");
        }
        return results.get(0);
    }

    RiderAddressReferenceBatchSaveResponse saveBatchAddressReferenceImage(String riderName, RiderBatchAddressReferenceRequest request) {
        requireRiderName(riderName);
        if (request == null || request.addressIds() == null || request.addressIds().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择至少一个地址");
        }
        if (isBlank(request.referenceImageUrl())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参考图不能为空");
        }

        LinkedHashSet<Long> uniqueAddressIds = new LinkedHashSet<>();
        for (Long addressId : request.addressIds()) {
            if (addressId != null && addressId > 0) {
                uniqueAddressIds.add(addressId);
            }
        }
        if (uniqueAddressIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择有效地址");
        }

        int updatedCount = 0;
        String normalizedReferenceImageUrl = riderReceiptStorageSupport.buildReceiptUrl(request.referenceImageUrl());
        for (Long addressId : uniqueAddressIds) {
            upsertAddressReferenceImage(addressId, normalizedReferenceImageUrl, null, riderName);
            updatedCount++;
        }
        return new RiderAddressReferenceBatchSaveResponse(updatedCount, new ArrayList<>(uniqueAddressIds));
    }

    RiderAddressReferenceReplaceResponse replaceAddressReferenceImage(String riderName, long addressId, String referenceImageUrl) {
        requireRiderName(riderName);
        if (addressId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "地址不能为空");
        }
        if (isBlank(referenceImageUrl)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参考图不能为空");
        }
        String normalizedReferenceImageUrl = riderReceiptStorageSupport.buildReceiptUrl(referenceImageUrl);
        upsertAddressReferenceImage(addressId, normalizedReferenceImageUrl, null, riderName);
        return new RiderAddressReferenceReplaceResponse(addressId, normalizedReferenceImageUrl, true);
    }

    DeliveryReceiptRecordResponse submitRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt) {
        requireRiderReceiptTask(mealSlotOrderId, riderName, true, "未找到可提交回执的配送任务");
        String finalReceiptUrl = isBlank(receiptFileKey)
            ? ""
            : riderReceiptStorageSupport.buildReceiptUrl(receiptFileKey);
        LocalDateTime deliveredDateTime = isBlank(deliveredAt)
            ? LocalDateTime.now().withNano(0)
            : LocalDateTime.parse(deliveredAt);
        LocalDateTime visibleAt = resolveReceiptVisibleAt(mealSlotOrderId, deliveredDateTime);
        LocalDateTime expiresAt = deliveredDateTime.plusHours(48);
        DeliveryReceiptRecordResponse result = deliveryService.recordDeliveryReceipt(
            mealSlotOrderId,
            finalReceiptUrl,
            normalizeNote(receiptNote),
            deliveredDateTime.toString(),
            visibleAt.toString(),
            expiresAt.toString()
        );
        jdbcTemplate.update(
            """
                UPDATE dispatch_batch_items
                SET item_status = 'DELIVERED'
                WHERE meal_slot_order_id = ?
                """,
            mealSlotOrderId
        );
        riderQueueSupport.refreshQueueStateForOrder(mealSlotOrderId);
        try {
            Long addressId = findAddressIdByMealSlotOrderId(mealSlotOrderId);
            saveAddressReferenceImageIfAbsent(addressId == null ? 0L : addressId, mealSlotOrderId, finalReceiptUrl, riderName);
        } catch (Exception ignored) {
            // Keep receipt submission successful even if reference-image auto-save fails.
        }
        try {
            deliverySubscriptionModule.sendAfterReceiptIfNeeded(mealSlotOrderId, deliveredDateTime);
        } catch (Exception ignored) {
            // Keep receipt submission successful even if notification delivery fails.
        }
        return result;
    }

    DeliveryReceiptRecordResponse updateRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt) {
        requireRiderReceiptTask(mealSlotOrderId, riderName, false, "未找到该配送任务");
        String previousReceiptUrl = requireExistingReceiptUrl(mealSlotOrderId);

        String finalReceiptUrl = isBlank(receiptFileKey)
            ? ""
            : riderReceiptStorageSupport.buildReceiptUrl(receiptFileKey);
        LocalDateTime deliveredDateTime = isBlank(deliveredAt)
            ? LocalDateTime.now().withNano(0)
            : LocalDateTime.parse(deliveredAt);
        LocalDateTime visibleAt = resolveReceiptVisibleAt(mealSlotOrderId, deliveredDateTime);
        LocalDateTime expiresAt = deliveredDateTime.plusHours(48);

        jdbcTemplate.update(
            """
                UPDATE delivery_receipts
                SET receipt_url = ?,
                    receipt_note = ?,
                    delivered_at = ?,
                    visible_at = ?,
                    expires_at = ?,
                    visible_to_customer = ?
                WHERE meal_slot_order_id = ?
                """,
            finalReceiptUrl,
            normalizeNote(receiptNote),
            Timestamp.valueOf(deliveredDateTime),
            Timestamp.valueOf(visibleAt),
            Timestamp.valueOf(expiresAt),
            !visibleAt.isAfter(LocalDateTime.now()),
            mealSlotOrderId
        );
        riderReceiptStorageSupport.deleteManagedReceiptFileQuietly(previousReceiptUrl, finalReceiptUrl);

        return new DeliveryReceiptRecordResponse(
            mealSlotOrderId,
            "DELIVERED",
            "UNCHANGED",
            "SKIPPED",
            finalReceiptUrl,
            visibleAt.toString(),
            expiresAt.toString()
        );
    }

    DeliveryReceiptDeleteResponse deleteRiderReceiptImage(long mealSlotOrderId, String riderName) {
        requireRiderReceiptTask(mealSlotOrderId, riderName, false, "未找到该配送任务");
        String previousReceiptUrl = requireExistingReceiptUrl(mealSlotOrderId);

        jdbcTemplate.update(
            """
                UPDATE delivery_receipts
                SET receipt_url = '',
                    visible_to_customer = FALSE
                WHERE meal_slot_order_id = ?
                """,
            mealSlotOrderId
        );
        riderReceiptStorageSupport.deleteManagedReceiptFileQuietly(previousReceiptUrl, "");

        return new DeliveryReceiptDeleteResponse(mealSlotOrderId, "DELIVERED", "", true);
    }

    private void requireRiderName(String riderName) {
        if (isBlank(riderName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "骑手姓名不能为空");
        }
    }

    private Long findAddressIdByMealSlotOrderId(long mealSlotOrderId) {
        List<Long> results = jdbcTemplate.query(
            """
                SELECT address_id
                FROM meal_slot_orders
                WHERE id = ?
                """,
            (rs, rowNum) -> rs.getLong("address_id"),
            mealSlotOrderId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    private void saveAddressReferenceImageIfAbsent(long addressId, long orderId, String receiptUrl, String riderName) {
        if (addressId <= 0 || isBlank(receiptUrl)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM address_reference_images
                WHERE customer_address_id = ?
                """,
            Integer.class,
            addressId
        );
        if (count != null && count > 0) {
            return;
        }
        upsertAddressReferenceImage(addressId, receiptUrl, orderId, riderName);
    }

    private void upsertAddressReferenceImage(long addressId, String referenceImageUrl, Long sourceOrderId, String riderName) {
        jdbcTemplate.update(
            """
                INSERT INTO address_reference_images (
                    customer_address_id,
                    reference_image_url,
                    source_order_id,
                    updated_by_rider_name
                ) VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    reference_image_url = VALUES(reference_image_url),
                    source_order_id = VALUES(source_order_id),
                    updated_by_rider_name = VALUES(updated_by_rider_name),
                    updated_at = CURRENT_TIMESTAMP
                """,
            addressId,
            referenceImageUrl,
            sourceOrderId,
            riderName
        );
    }

    private void requireRiderReceiptTask(long mealSlotOrderId, String riderName, boolean requireDispatchingStatus, String notFoundMessage) {
        String sql = requireDispatchingStatus
            ? """
                SELECT COUNT(*)
                FROM dispatch_assignments
                WHERE meal_slot_order_id = ? AND rider_name = ? AND status = 'DISPATCHING'
                """
            : """
                SELECT COUNT(*)
                FROM dispatch_assignments
                WHERE meal_slot_order_id = ? AND rider_name = ?
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, mealSlotOrderId, riderName);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RIDER_TASK_NOT_FOUND, notFoundMessage);
        }
    }

    private String requireExistingReceiptUrl(long mealSlotOrderId) {
        Integer receiptCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM delivery_receipts
                WHERE meal_slot_order_id = ?
                """,
            Integer.class,
            mealSlotOrderId
        );
        if (receiptCount == null || receiptCount == 0) {
            throw new BusinessException(ErrorCode.RIDER_TASK_NOT_FOUND, "未找到回执记录");
        }
        return jdbcTemplate.queryForObject(
            """
                SELECT receipt_url
                FROM delivery_receipts
                WHERE meal_slot_order_id = ?
                """,
            String.class,
            mealSlotOrderId
        );
    }

    private LocalDateTime resolveReceiptVisibleAt(long mealSlotOrderId, LocalDateTime deliveredDateTime) {
        MealSlotContext row = loadMealSlotContext(mealSlotOrderId);
        LocalDateTime threshold = deliverySubscriptionModule.resolveDeliveryNotifyThreshold(row.serveDate(), row.mealPeriod());
        return deliveredDateTime.isBefore(threshold) ? threshold : deliveredDateTime;
    }

    private MealSlotContext loadMealSlotContext(long mealSlotOrderId) {
        return jdbcTemplate.query(
            """
                SELECT do.serve_date, mso.meal_period
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE mso.id = ?
                """,
            ps -> ps.setLong(1, mealSlotOrderId),
            rs -> {
                if (!rs.next()) {
                    throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
                }
                return new MealSlotContext(
                    rs.getObject("serve_date", LocalDate.class),
                    rs.getString("meal_period")
                );
            }
        );
    }

    private String normalizeNote(String note) {
        return isBlank(note) ? "-" : note.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record MealSlotContext(LocalDate serveDate, String mealPeriod) {
    }
}
