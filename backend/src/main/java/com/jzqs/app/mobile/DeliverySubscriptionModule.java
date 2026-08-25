package com.jzqs.app.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.wechat.WeChatService;
import com.jzqs.app.settings.service.SettingsService;
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
    private final SettingsService settingsService;

    DeliverySubscriptionModule(JdbcTemplate jdbcTemplate, WeChatService weChatService, ObjectMapper objectMapper, SettingsService settingsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.weChatService = weChatService;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
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
        return releaseAndSendWithReason(mealSlotOrderId).sent();
    }

    /** 与 {@link #releaseAndSend(long)} 等价，但额外返回发送原因，便于后台排障展示 */
    DeliverySendResult releaseAndSendWithReason(long mealSlotOrderId) {
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
        return trySendDeliverySubscriptionWithReason(mealSlotOrderId, LocalDateTime.now().withNano(0));
    }

    /** 取餐订阅消息发送结果：是否成功发送 + 原因（SENT/NO_CONSENT/SEND_FAILED/DISABLED） */
    public record DeliverySendResult(boolean sent, String reason) {
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
        // 扫描"已送达、回执尚未对用户可见、且已到餐期释放时间"的订单。
        // 不限定必须有订阅记录：无论有无订阅，到点都应把回执对用户可见（自动释放），
        // 这样订单会从后台"待释放"列表消失；订阅消息仅在有授权时补发。
        List<Long> orderIds = jdbcTemplate.query(
            """
            SELECT mso.id
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
            WHERE mso.status = 'DELIVERED'
              AND mso.meal_period = ?
              AND do.serve_date = ?
              AND dr.visible_to_customer = FALSE
              AND TIMESTAMP(do.serve_date, ?) <= ?
            ORDER BY mso.id
            """,
            (rs, rowNum) -> rs.getLong(1),
            mealPeriod,
            serveDate,
            releaseTime,
            Timestamp.valueOf(now)
        );
        int sentCount = 0;
        for (Long orderId : orderIds) {
            // 先把回执对用户可见（与后台手动"立即释放"等价），保证订单即时从待释放列表消失
            jdbcTemplate.update(
                """
                UPDATE delivery_receipts
                SET visible_at = ?,
                    visible_to_customer = TRUE
                WHERE meal_slot_order_id = ? AND visible_to_customer = FALSE
                """,
                Timestamp.valueOf(now),
                orderId
            );
            // 再尝试发送取餐提醒订阅消息（无授权记录时仅释放、不发送）
            if (trySendDeliverySubscription(orderId, now)) {
                sentCount++;
            }
        }
        // 所有订单处理完毕后，统一清理一次过期订阅记录（原先在循环内每个订单各清一次，浪费连接）
        pruneOldDeliverySubscriptions();
        return sentCount;
    }

    private boolean trySendDeliverySubscription(long mealSlotOrderId, LocalDateTime triggerTime) {
        return trySendDeliverySubscriptionWithReason(mealSlotOrderId, triggerTime).sent();
    }

    private DeliverySendResult trySendDeliverySubscriptionWithReason(long mealSlotOrderId, LocalDateTime triggerTime) {
        // 「订阅通知发送」开关关闭时，所有取餐订阅消息（含定时调度、后台立即释放、测试发送）统一不再发送
        if (!settingsService.operationSettings().deliverySubscribeEnabled()) {
            log.debug("订阅通知发送已关闭，跳过订单 {} 的取餐订阅消息", mealSlotOrderId);
            return new DeliverySendResult(false, "DISABLED");
        }
        DeliverySubscriptionSendContext context = findDeliverySubscriptionSendContext(mealSlotOrderId);
        // 该订单本身没有订阅记录（典型场景：商家后台代客下单，未走小程序授权流程）。
        // 回退到「该客户已授权过取餐模板」维度：只要该客户存在一条有效的取餐订阅授权，
        // 即视为已取得下发许可（用户在小程序点过「总是允许」），为该订单补写记录后照常发送。
        if (context == null) {
            context = ensureDeliverySubscriptionFromCustomerConsent(mealSlotOrderId);
        }
        if (context == null || isBlank(context.openid())) {
            return new DeliverySendResult(false, "NO_CONSENT");
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
            return new DeliverySendResult(true, "SENT");
        } catch (Exception ex) {
            jdbcTemplate.update(
                "UPDATE customer_delivery_subscriptions SET status = 'FAILED', last_error_message = ? WHERE id = ?",
                ex.getMessage(),
                context.id()
            );
            return new DeliverySendResult(false, "SEND_FAILED");
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

    /**
     * 回退策略（方案 B）：当指定订单没有 customer_delivery_subscriptions 记录时，
     * 检查其所属客户是否已有任意一条有效的取餐订阅授权（AUTHORIZED/FAILED）。
     * 若有，则为该订单补写一条取餐订阅记录（沿用客户已授权的 template_id），
     * 以便取餐提醒能正常下发。这样「用户在小程序点过总是允许 + 商家后台代客下单」的场景
     * 也能收到取餐提醒，而不必每单都重复授权。
     *
     * @return 补写成功后返回该订单的发送上下文；若客户从未授权过取餐模板则返回 null
     */
    private DeliverySubscriptionSendContext ensureDeliverySubscriptionFromCustomerConsent(long mealSlotOrderId) {
        Long customerId = jdbcTemplate.queryForObject(
            """
            SELECT do.customer_id
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE mso.id = ?
            """,
            (rs, rowNum) -> rs.getLong(1),
            mealSlotOrderId
        );
        if (customerId == null) {
            return null;
        }
        String templateId = jdbcTemplate.query(
            """
            SELECT template_id
            FROM customer_delivery_subscriptions
            WHERE customer_id = ? AND status IN ('AUTHORIZED', 'FAILED')
            ORDER BY authorized_at DESC
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getString(1),
            customerId
        ).stream().findFirst().orElse(null);
        if (templateId == null) {
            log.debug("客户 {} 没有有效的取餐订阅授权，订单 {} 不发送取餐提醒", customerId, mealSlotOrderId);
            return null;
        }
        // 为该订单补写取餐订阅记录；meal_slot_order_id 唯一键，使用 INSERT IGNORE 防止并发/重复写入冲突
        int inserted = jdbcTemplate.update(
            """
            INSERT IGNORE INTO customer_delivery_subscriptions (
                customer_id, meal_slot_order_id, template_id, status, source, authorized_at
            ) VALUES (?, ?, ?, 'AUTHORIZED', 'INHERITED_FROM_CUSTOMER_CONSENT', CURRENT_TIMESTAMP)
            """,
            customerId,
            mealSlotOrderId,
            templateId
        );
        if (inserted > 0) {
            pruneOldDeliverySubscriptions();
        }
        return findDeliverySubscriptionSendContext(mealSlotOrderId);
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
