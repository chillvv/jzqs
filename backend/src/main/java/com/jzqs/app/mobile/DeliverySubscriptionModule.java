package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.wechat.WeChatService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class DeliverySubscriptionModule {
    private static final Logger log = LoggerFactory.getLogger(DeliverySubscriptionModule.class);
    private static final int DELIVERY_SUBSCRIPTION_RETENTION_DAYS = 30;

    private final JdbcTemplate jdbcTemplate;
    private final WeChatService weChatService;

    DeliverySubscriptionModule(JdbcTemplate jdbcTemplate, WeChatService weChatService) {
        this.jdbcTemplate = jdbcTemplate;
        this.weChatService = weChatService;
    }

    void authorizeSubscription(long customerId, long orderId, String templateId) {
        jdbcTemplate.update(
            """
            INSERT INTO customer_delivery_subscriptions (
                customer_id, meal_slot_order_id, template_id, status, source, authorized_at
            ) VALUES (?, ?, ?, 'AUTHORIZED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                customer_id = VALUES(customer_id),
                template_id = VALUES(template_id),
                status = 'AUTHORIZED',
                source = VALUES(source),
                authorized_at = VALUES(authorized_at),
                sent_at = NULL,
                last_error_message = NULL
            """,
            customerId,
            orderId,
            templateId
        );
        pruneOldDeliverySubscriptions();
    }

    void sendTestMessage(long customerId) {
        DeliverySubscriptionSendContext context = findCustomerSubscribeMessageTestContext(customerId);
        if (context == null || isBlank(context.openid())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前账号缺少可用的微信接收标识");
        }
        weChatService.sendDeliverySubscribeMessage(
            context.openid(),
            "pages/profile/index",
            "简知轻食",
            "请查看取餐测试提醒"
        );
    }

    int sendScheduledMessages(String mealPeriod) {
        String normalizedMealPeriod = normalizeMealPeriod(mealPeriod);
        if (normalizedMealPeriod == null) {
            return 0;
        }
        return sendScheduledMessagesInternal(normalizedMealPeriod, LocalDate.now(), LocalDateTime.now().withNano(0));
    }

    int sendAllDeliveredPendingSubscriptions() {
        List<Long> orderIds = jdbcTemplate.query(
            """
            SELECT cds.meal_slot_order_id
            FROM customer_delivery_subscriptions cds
            JOIN meal_slot_orders mso ON mso.id = cds.meal_slot_order_id
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            WHERE cds.status IN ('AUTHORIZED', 'FAILED')
              AND mso.status = 'DELIVERED'
              AND COALESCE(c.current_openid, c.openid, '') <> ''
            ORDER BY cds.meal_slot_order_id
            """,
            (rs, rowNum) -> rs.getLong(1)
        );
        int sentCount = 0;
        LocalDateTime now = LocalDateTime.now().withNano(0);
        for (Long orderId : orderIds) {
            if (trySendDeliverySubscription(orderId, now)) {
                sentCount++;
            }
        }
        return sentCount;
    }

    void sendAfterReceiptIfNeeded(long mealSlotOrderId, LocalDateTime deliveredDateTime) {
        if (isDeliverySubscribeFixedTimeEnabled()) {
            return;
        }
        trySendDeliverySubscription(mealSlotOrderId, deliveredDateTime);
    }

    LocalDateTime resolveDeliveryNotifyThreshold(LocalDate serveDate, String mealPeriod) {
        LocalTime cutoff = "DINNER".equalsIgnoreCase(mealPeriod) ? LocalTime.of(17, 0) : LocalTime.of(11, 30);
        return LocalDateTime.of(serveDate, cutoff);
    }

    boolean hasReachedDeliveryNotifyCutoff(String mealPeriod, LocalDate serveDate, LocalDateTime now) {
        if (serveDate == null || mealPeriod == null || now == null) {
            return false;
        }
        return !now.isBefore(resolveDeliveryNotifyThreshold(serveDate, mealPeriod));
    }

    private int sendScheduledMessagesInternal(String mealPeriod, LocalDate serveDate, LocalDateTime now) {
        List<Long> orderIds = jdbcTemplate.query(
            """
            SELECT cds.meal_slot_order_id
            FROM customer_delivery_subscriptions cds
            JOIN meal_slot_orders mso ON mso.id = cds.meal_slot_order_id
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE cds.status IN ('AUTHORIZED', 'FAILED')
              AND mso.status = 'DELIVERED'
              AND mso.meal_period = ?
              AND do.serve_date = ?
            ORDER BY cds.meal_slot_order_id
            """,
            (rs, rowNum) -> rs.getLong(1),
            mealPeriod,
            serveDate
        );
        int sentCount = 0;
        for (Long orderId : orderIds) {
            if (trySendDeliverySubscription(orderId, now)) {
                sentCount++;
            }
        }
        return sentCount;
    }

    private boolean trySendDeliverySubscription(long mealSlotOrderId, LocalDateTime triggerTime) {
        DeliverySubscriptionSendContext context = findDeliverySubscriptionSendContext(mealSlotOrderId);
        if (context == null || isBlank(context.openid())) {
            return false;
        }
        try {
            weChatService.sendDeliverySubscribeMessage(
                context.openid(),
                weChatService.buildDeliveryPage(mealSlotOrderId),
                "简知轻食",
                "您的餐食已送达，可查看回执照片与备注"
            );
            jdbcTemplate.update(
                "UPDATE customer_delivery_subscriptions SET status = 'SENT', sent_at = ?, last_error_message = NULL WHERE id = ?",
                Timestamp.valueOf(triggerTime),
                context.id()
            );
            pruneOldDeliverySubscriptions();
            return true;
        } catch (Exception ex) {
            jdbcTemplate.update(
                "UPDATE customer_delivery_subscriptions SET status = 'FAILED', last_error_message = ? WHERE id = ?",
                ex.getMessage(),
                context.id()
            );
            pruneOldDeliverySubscriptions();
            throw ex;
        }
    }

    private void pruneOldDeliverySubscriptions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(DELIVERY_SUBSCRIPTION_RETENTION_DAYS);
        int deletedCount = jdbcTemplate.update(
            """
            DELETE FROM customer_delivery_subscriptions
            WHERE COALESCE(sent_at, authorized_at) < ?
            """,
            Timestamp.valueOf(cutoffTime)
        );
        if (deletedCount > 0) {
            log.info("清理配送订阅状态记录: {}", deletedCount);
        }
    }

    private DeliverySubscriptionSendContext findDeliverySubscriptionSendContext(long mealSlotOrderId) {
        return jdbcTemplate.query(
            """
            SELECT
                cds.id,
                COALESCE(c.current_openid, c.openid, '') AS current_openid
            FROM customer_delivery_subscriptions cds
            JOIN meal_slot_orders mso ON mso.id = cds.meal_slot_order_id
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            WHERE cds.meal_slot_order_id = ?
              AND cds.status IN ('AUTHORIZED', 'FAILED')
            """,
            ps -> ps.setLong(1, mealSlotOrderId),
            rs -> rs.next()
                ? new DeliverySubscriptionSendContext(
                    rs.getLong("id"),
                    rs.getString("current_openid")
                )
                : null
        );
    }

    private DeliverySubscriptionSendContext findCustomerSubscribeMessageTestContext(long customerId) {
        return jdbcTemplate.query(
            """
            SELECT COALESCE(current_openid, openid, '') AS current_openid
            FROM customers
            WHERE id = ? AND active = TRUE
            """,
            ps -> ps.setLong(1, customerId),
            rs -> rs.next()
                ? new DeliverySubscriptionSendContext(
                    0L,
                    rs.getString("current_openid")
                )
                : null
        );
    }

    private boolean isDeliverySubscribeFixedTimeEnabled() {
        Boolean enabled = jdbcTemplate.query(
            """
            SELECT delivery_subscribe_enabled
            FROM admin_settings
            WHERE id = 1
            """,
            rs -> rs.next() ? rs.getBoolean("delivery_subscribe_enabled") : Boolean.FALSE
        );
        return Boolean.TRUE.equals(enabled);
    }

    private String normalizeMealPeriod(String mealPeriod) {
        String normalized = mealPeriod == null ? "" : mealPeriod.trim().toUpperCase();
        if (!"LUNCH".equals(normalized) && !"DINNER".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record DeliverySubscriptionSendContext(
        long id,
        String openid
    ) {
    }
}
