package com.jzqs.app.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    /** 餐期送达状态对用户可见/订阅消息发送的释放时间默认值；实际以 admin_settings 配置（delivery_subscribe_lunch_time / delivery_subscribe_dinner_time）为准 */
    static final LocalTime RELEASE_LUNCH_TIME_DEFAULT = LocalTime.of(11, 30);
    static final LocalTime RELEASE_DINNER_TIME_DEFAULT = LocalTime.of(17, 0);

    private final JdbcTemplate jdbcTemplate;
    private final WeChatService weChatService;
    private final ObjectMapper objectMapper;

    DeliverySubscriptionModule(JdbcTemplate jdbcTemplate, WeChatService weChatService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.weChatService = weChatService;
        this.objectMapper = objectMapper;
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

    String sendTestMessage(long customerId) {
        DeliverySubscriptionSendContext context = findCustomerSubscribeMessageTestContext(customerId);
        if (context == null || isBlank(context.openid())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前账号缺少可用的微信接收标识");
        }
        weChatService.sendDeliverySubscribeMessage(
            context.openid(),
            "pages/profile/index",
            "今日套餐",
            "13800138000",
            "简知轻食取餐点",
            "请查看取餐测试提醒"
        );
        return "pages/profile/index";
    }

    int sendScheduledMessages(String mealPeriod) {
        String normalizedMealPeriod = normalizeMealPeriod(mealPeriod);
        if (normalizedMealPeriod == null) {
            return 0;
        }
        return sendScheduledMessagesInternal(normalizedMealPeriod, LocalDate.now(), LocalDateTime.now().withNano(0));
    }

    int sendAllDeliveredPendingSubscriptions() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String lunchReleaseTime = resolveConfiguredReleaseTime("LUNCH").toString();
        String dinnerReleaseTime = resolveConfiguredReleaseTime("DINNER").toString();
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
              AND (
                    (mso.meal_period = 'LUNCH' AND TIMESTAMP(do.serve_date, ?) <= ?)
                 OR (mso.meal_period = 'DINNER' AND TIMESTAMP(do.serve_date, ?) <= ?)
              )
            ORDER BY cds.meal_slot_order_id
            """,
            (rs, rowNum) -> rs.getLong(1),
            lunchReleaseTime,
            Timestamp.valueOf(now),
            dinnerReleaseTime,
            Timestamp.valueOf(now)
        );
        int sentCount = 0;
        for (Long orderId : orderIds) {
            if (trySendDeliverySubscription(orderId, now)) {
                sentCount++;
            }
        }
        return sentCount;
    }

    /**
     * 后台手动"立即释放"：将订单回执对用户可见，并立即发送取餐提醒订阅消息。
     * 返回是否实际发送了订阅消息（无授权订阅时不发送，仅释放状态）。
     */
    boolean releaseAndSend(long mealSlotOrderId) {
        int updated = jdbcTemplate.update(
            """
                UPDATE delivery_receipts
                SET visible_at = CURRENT_TIMESTAMP,
                    visible_to_customer = TRUE
                WHERE meal_slot_order_id = ?
                """,
            mealSlotOrderId
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "未找到该订单的回执记录");
        }
        return trySendDeliverySubscription(mealSlotOrderId, LocalDateTime.now().withNano(0));
    }

    /** 餐期释放时间：优先取系统设置里配置的订阅发送时间，未配置/异常时回退默认值 */
    LocalTime resolveConfiguredReleaseTime(String mealPeriod) {
        String column = "DINNER".equalsIgnoreCase(mealPeriod)
            ? "delivery_subscribe_dinner_time"
            : "delivery_subscribe_lunch_time";
        try {
            String configured = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM admin_settings WHERE id = 1",
                String.class
            );
            if (configured != null && !configured.isBlank()) {
                return LocalTime.parse(configured.trim());
            }
        } catch (RuntimeException ex) {
            log.warn("读取餐期释放时间配置失败 mealPeriod={} reason={}", mealPeriod, ex.getMessage());
        }
        return "DINNER".equalsIgnoreCase(mealPeriod) ? RELEASE_DINNER_TIME_DEFAULT : RELEASE_LUNCH_TIME_DEFAULT;
    }

    LocalDateTime resolveDeliveryNotifyThreshold(LocalDate serveDate, String mealPeriod) {
        LocalTime cutoff = resolveConfiguredReleaseTime(mealPeriod);
        return LocalDateTime.of(serveDate, cutoff);
    }

    boolean hasReachedDeliveryNotifyCutoff(String mealPeriod, LocalDate serveDate, LocalDateTime now) {
        if (serveDate == null || mealPeriod == null || now == null) {
            return false;
        }
        return !now.isBefore(resolveDeliveryNotifyThreshold(serveDate, mealPeriod));
    }

    private int sendScheduledMessagesInternal(String mealPeriod, LocalDate serveDate, LocalDateTime now) {
        String releaseTime = resolveConfiguredReleaseTime(mealPeriod).toString();
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
              AND TIMESTAMP(do.serve_date, ?) <= ?
            ORDER BY cds.meal_slot_order_id
            """,
            (rs, rowNum) -> rs.getLong(1),
            mealPeriod,
            serveDate,
            releaseTime,
            Timestamp.valueOf(now)
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
                context.dishNames(),
                context.riderPhone(),
                context.pickupLocation(),
                "防止他人误拿，请半小时内尽快取走哈"
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
                COALESCE(c.current_openid, c.openid, '') AS current_openid,
                mwi.dish_items_json AS dish_items_json,
                rp.phone AS rider_phone,
                ca.address_line AS pickup_location
            FROM customer_delivery_subscriptions cds
            JOIN meal_slot_orders mso ON mso.id = cds.meal_slot_order_id
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            LEFT JOIN menu_week_items mwi
                ON mwi.serve_date = do.serve_date AND mwi.meal_period = mso.meal_period
            LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id = mso.id
            LEFT JOIN rider_profiles rp ON rp.id = da.rider_profile_id
            LEFT JOIN customer_addresses ca ON ca.id = mso.address_id
            WHERE cds.meal_slot_order_id = ?
              AND cds.status IN ('AUTHORIZED', 'FAILED')
            """,
            ps -> ps.setLong(1, mealSlotOrderId),
            rs -> rs.next()
                ? new DeliverySubscriptionSendContext(
                    rs.getLong("id"),
                    rs.getString("current_openid"),
                    parseDishNames(rs.getString("dish_items_json")),
                    rs.getString("rider_phone"),
                    rs.getString("pickup_location")
                )
                : null
        );
    }

    /** 解析 menu_week_items.dish_items_json（字符串数组），用「、」拼接为商品名；为空时回退为商家套餐描述 */
    private String parseDishNames(String dishItemsJson) {
        if (isBlank(dishItemsJson)) {
            return "今日营养套餐";
        }
        try {
            List<String> dishes = objectMapper.readValue(
                dishItemsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            if (dishes == null || dishes.isEmpty()) {
                return "今日营养套餐";
            }
            return String.join("、", dishes);
        } catch (Exception ex) {
            log.warn("解析菜品名 JSON 失败，使用原始文本 dishItemsJson={}", dishItemsJson);
            return dishItemsJson.replaceAll("[\\[\\]\"]", "").trim();
        }
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
                ?                 new DeliverySubscriptionSendContext(
                    0L,
                    rs.getString("current_openid"),
                    "",
                    "",
                    ""
                )
                : null
        );
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
        String openid,
        String dishNames,
        String riderPhone,
        String pickupLocation
    ) {
    }
}
