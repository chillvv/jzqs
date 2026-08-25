package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.delivery.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.delivery.service.DeliveryService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商家后台（骑手进度页）提交/修改送达回执的门面。
 *
 * <p>与骑手端 {@code RiderDeliveryEvidenceModule} 共用同一套回执可见性规则：
 * 回执先对用户隐藏，到餐期释放时间（午餐 11:30 / 晚餐 17:00）或后台手动提前释放后才可见，
 * 并同步 dispatch_batch_items、骑手队列状态与实时事件，确保商家操作与骑手操作效果完全一致。
 *
 * <p>放在 {@code com.jzqs.app.mobile} 包内是为了复用该包内的包私有协作者
 * （RiderQueueSupport / RiderReceiptStorageSupport / DeliverySubscriptionModule），
 * 同时对外只暴露本类的 public 方法。
 */
@Component
public class MerchantDeliveryReceiptFacade {
    private static final int RECEIPT_VISIBLE_HOURS = 48;

    private final JdbcTemplate jdbcTemplate;
    private final DeliveryService deliveryService;
    private final RiderQueueSupport riderQueueSupport;
    private final RiderReceiptStorageSupport riderReceiptStorageSupport;
    private final DeliverySubscriptionModule deliverySubscriptionModule;
    private final RealtimeAudienceModule realtimeAudienceModule;

    MerchantDeliveryReceiptFacade(
        JdbcTemplate jdbcTemplate,
        DeliveryService deliveryService,
        RiderQueueSupport riderQueueSupport,
        RiderReceiptStorageSupport riderReceiptStorageSupport,
        DeliverySubscriptionModule deliverySubscriptionModule,
        RealtimeAudienceModule realtimeAudienceModule
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.deliveryService = deliveryService;
        this.riderQueueSupport = riderQueueSupport;
        this.riderReceiptStorageSupport = riderReceiptStorageSupport;
        this.deliverySubscriptionModule = deliverySubscriptionModule;
        this.realtimeAudienceModule = realtimeAudienceModule;
    }

    /**
     * 商家提交或修改回执。已有回执记录时按"修改"处理（不重复核销钱包），否则按"首次送达"处理。
     *
     * @param receiptFileKey 上传接口返回的文件路径，允许为空表示暂不上传图片
     * @param deliveredAt    送达时间，为空时取当前时间
     */
    @Transactional
    public DeliveryReceiptRecordResponse submitMerchantReceipt(
        long mealSlotOrderId,
        String receiptFileKey,
        String receiptNote,
        String deliveredAt
    ) {
        requireOrderExists(mealSlotOrderId);

        String finalReceiptUrl = isBlank(receiptFileKey)
            ? ""
            : riderReceiptStorageSupport.buildReceiptUrl(receiptFileKey);
        LocalDateTime deliveredDateTime = parseDeliveredAt(deliveredAt);
        LocalDateTime visibleAt = resolveReceiptVisibleAt(mealSlotOrderId, deliveredDateTime);
        LocalDateTime expiresAt = deliveredDateTime.plusHours(RECEIPT_VISIBLE_HOURS);

        return hasReceipt(mealSlotOrderId)
            ? updateExistingReceipt(mealSlotOrderId, finalReceiptUrl, receiptNote, deliveredDateTime, visibleAt, expiresAt)
            : createReceipt(mealSlotOrderId, finalReceiptUrl, receiptNote, deliveredDateTime, visibleAt, expiresAt);
    }

    /** 商家删除回执图片，保留回执记录与送达状态。 */
    @Transactional
    public DeliveryReceiptDeleteResponse deleteMerchantReceiptImage(long mealSlotOrderId) {
        requireOrderExists(mealSlotOrderId);
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
        publishDeliveryStateEvents(mealSlotOrderId);

        return new DeliveryReceiptDeleteResponse(mealSlotOrderId, "DELIVERED", "", true);
    }

    private DeliveryReceiptRecordResponse createReceipt(
        long mealSlotOrderId,
        String finalReceiptUrl,
        String receiptNote,
        LocalDateTime deliveredDateTime,
        LocalDateTime visibleAt,
        LocalDateTime expiresAt
    ) {
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
        publishDeliveryStateEvents(mealSlotOrderId);
        return result;
    }

    private DeliveryReceiptRecordResponse updateExistingReceipt(
        long mealSlotOrderId,
        String finalReceiptUrl,
        String receiptNote,
        LocalDateTime deliveredDateTime,
        LocalDateTime visibleAt,
        LocalDateTime expiresAt
    ) {
        String previousReceiptUrl = requireExistingReceiptUrl(mealSlotOrderId);

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
        publishDeliveryStateEvents(mealSlotOrderId);

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

    private LocalDateTime parseDeliveredAt(String deliveredAt) {
        if (isBlank(deliveredAt)) {
            return LocalDateTime.now().withNano(0);
        }
        try {
            return LocalDateTime.parse(deliveredAt.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "送达时间格式不正确");
        }
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

    private void requireOrderExists(long mealSlotOrderId) {
        if (mealSlotOrderId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "订单不能为空");
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE id = ?",
            Integer.class,
            mealSlotOrderId
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
    }

    private boolean hasReceipt(long mealSlotOrderId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM delivery_receipts WHERE meal_slot_order_id = ?",
            Integer.class,
            mealSlotOrderId
        );
        return count != null && count > 0;
    }

    private String requireExistingReceiptUrl(long mealSlotOrderId) {
        if (!hasReceipt(mealSlotOrderId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "未找到回执记录");
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

    private void publishDeliveryStateEvents(long mealSlotOrderId) {
        try {
            Long customerId = jdbcTemplate.query(
                """
                    SELECT do.customer_id
                    FROM meal_slot_orders mso
                    JOIN daily_orders do ON do.id = mso.daily_order_id
                    WHERE mso.id = ?
                    """,
                ps -> ps.setLong(1, mealSlotOrderId),
                rs -> rs.next() ? rs.getLong(1) : null
            );
            if (customerId != null && customerId > 0) {
                realtimeAudienceModule.publishCustomerEvent("customer.order.changed", customerId, mealSlotOrderId);
            }
            realtimeAudienceModule.publishDispatchEvent("dispatch.queue.changed", null, null, mealSlotOrderId);
        } catch (RuntimeException ignored) {
            // Keep receipt submission successful even if realtime publish fails.
        }
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
