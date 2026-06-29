package com.jzqs.app.mobile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeEvent;
import com.jzqs.app.common.realtime.TransactionalRealtimePublisher;
import com.jzqs.app.common.wechat.WeChatService;
import com.jzqs.app.customer.api.WalletTransactionResponse;
import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.delivery.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.delivery.service.DeliveryService;
import com.jzqs.app.mobile.api.MobileAddressResponse;
import com.jzqs.app.mobile.api.MobileAfterSaleItemResponse;
import com.jzqs.app.mobile.api.MobileBannerItemResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleResponse;
import com.jzqs.app.mobile.api.MobileCreateOrderResponse;
import com.jzqs.app.mobile.api.MobileDeliverySubscriptionAuthorizeResponse;
import com.jzqs.app.mobile.api.MobileCurrentWeekDayResponse;
import com.jzqs.app.mobile.api.MobileCurrentWeekResponse;
import com.jzqs.app.mobile.api.MobileDefaultAddressResponse;
import com.jzqs.app.mobile.api.MobileHomeResponse;
import com.jzqs.app.mobile.api.MobileMenuItemResponse;
import com.jzqs.app.mobile.api.MobileOrderAddressChangeResponse;
import com.jzqs.app.mobile.api.MobileOrderItemResponse;
import com.jzqs.app.mobile.api.MobileSubscribeMessageTestResponse;
import com.jzqs.app.mobile.api.MobileTomorrowMenuResponse;
import com.jzqs.app.mobile.api.MobileWeekMenuDayResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceBatchSaveResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceReplaceResponse;
import com.jzqs.app.mobile.api.RiderBatchAddressReferenceRequest;
import com.jzqs.app.mobile.api.RiderBatchSummaryResponse;
import com.jzqs.app.mobile.api.RiderDeliveryExceptionReportResponse;
import com.jzqs.app.order.api.OrderActionResponse;
import com.jzqs.app.mobile.api.RiderDeliveryUploadResponse;
import com.jzqs.app.mobile.api.RiderOrderStatusRevertResponse;
import com.jzqs.app.mobile.api.RiderOrderSequenceSaveResponse;
import com.jzqs.app.mobile.api.RiderQueueItemActionResponse;
import com.jzqs.app.mobile.api.RiderQueueItemResponse;
import com.jzqs.app.mobile.api.RiderQueueReorderResponse;
import com.jzqs.app.mobile.api.RiderTaskItemResponse;
import com.jzqs.app.order.service.OrderPrepService;
import com.jzqs.app.order.service.OrderNoteSnapshotService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MobilePortalServiceImpl implements MobilePortalService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DELIVERY_SUBSCRIPTION_RETENTION_DAYS = 30;
    private static final Logger log = LoggerFactory.getLogger(MobilePortalServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;
    private final OrderPrepService orderPrepService;
    private final OrderNoteSnapshotService orderNoteSnapshotService;
    private final DeliveryService deliveryService;
    private final DispatchService dispatchService;
    private final RiderQueueSupport riderQueueSupport;
    private final RiderReceiptStorageSupport riderReceiptStorageSupport;
    private final ObjectMapper objectMapper;
    private final MobilePortalServiceExtension extension;
    private final AftersaleService aftersaleService;
    private final WeChatService weChatService;
    private final TransactionalRealtimePublisher realtimeEventPublisher;
    private final LocalTime selfOrderCutoffTime;

    public MobilePortalServiceImpl(
        JdbcTemplate jdbcTemplate,
        OrderPrepService orderPrepService,
        OrderNoteSnapshotService orderNoteSnapshotService,
        DeliveryService deliveryService,
        DispatchService dispatchService,
        RiderQueueSupport riderQueueSupport,
        RiderReceiptStorageSupport riderReceiptStorageSupport,
        ObjectMapper objectMapper,
        MobilePortalServiceExtension extension,
        AftersaleService aftersaleService,
        WeChatService weChatService,
        TransactionalRealtimePublisher realtimeEventPublisher,
        @org.springframework.beans.factory.annotation.Value("${app.mobile.self-order-cutoff:23:00}") String selfOrderCutoff
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderPrepService = orderPrepService;
        this.orderNoteSnapshotService = orderNoteSnapshotService;
        this.deliveryService = deliveryService;
        this.dispatchService = dispatchService;
        this.riderQueueSupport = riderQueueSupport;
        this.riderReceiptStorageSupport = riderReceiptStorageSupport;
        this.objectMapper = objectMapper;
        this.extension = extension;
        this.aftersaleService = aftersaleService;
        this.weChatService = weChatService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.selfOrderCutoffTime = LocalTime.parse(selfOrderCutoff);
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public MobileHomeResponse customerHome(String phone) {
        return customerHome(findCustomerIdByPhone(phone));
    }

    public MobileHomeResponse guestHome() {
        AdminSettingsSnapshot settings = loadAdminSettingsSnapshot();
        boolean orderingEnabled = settings.orderingEnabled();
        return new MobileHomeResponse(
            0L,
            "微信用户",
            "",
            "未开通套餐",
            0,
            0,
            "",
            0,
            "",
            "",
            orderingEnabled,
            orderingEnabled ? "可浏览菜单" : "暂停接单",
            settings.holidayNoticeTitle(),
            settings.holidayNoticeDesc(),
            "",
            "",
            "",
            parseBannerImages(settings.bannerImages()),
            resolveBannerIntervalMs(settings.bannerIntervalSeconds()),
            settings.popupAnnouncementEnabled(),
            settings.popupAnnouncementContent(),
            false,
            "",
            "",
            ""
        );
    }

    public MobileHomeResponse customerHome(long customerId) {
        CustomerHomeSnapshot customer = jdbcTemplate.query("""
            SELECT
                c.id,
                c.name,
                c.phone,
                COALESCE(pp.package_name, '未开通套餐') AS package_name,
                COALESCE(mw.total_meals, 0) AS total_meals,
                COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) AS remaining_meals,
                mw.expired_at,
                c.merchant_remark,
                (
                    SELECT ca.address_line
                    FROM customer_addresses ca
                    WHERE ca.customer_id = c.id
                    ORDER BY ca.is_default DESC, ca.id ASC
                    LIMIT 1
                ) AS default_address,
                (
                    SELECT cn.content
                    FROM customer_notes cn
                    WHERE cn.customer_id = c.id
                    AND cn.note_type = 'USER'
                    AND cn.scope_type = 'LONG_TERM'
                    AND cn.is_active = TRUE
                    ORDER BY cn.display_order, cn.id
                    LIMIT 1
                ) AS default_user_remark
            FROM customers c
            LEFT JOIN meal_wallets mw ON mw.customer_id = c.id AND mw.active = TRUE
            LEFT JOIN package_plans pp ON pp.id = mw.package_plan_id
            WHERE c.id = ? AND c.active = TRUE
            """, ps -> ps.setLong(1, customerId), rs -> {
            if (!rs.next()) {
                return null;
            }
            Timestamp expiredAt = rs.getTimestamp("expired_at");
            return new CustomerHomeSnapshot(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("package_name"),
                rs.getInt("total_meals"),
                rs.getInt("remaining_meals"),
                expiredAt == null ? null : expiredAt.toLocalDateTime(),
                rs.getString("merchant_remark"),
                rs.getString("default_address"),
                rs.getString("default_user_remark")
            );
        });
        if (customer == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应客户");
        }
        AdminSettingsSnapshot settings = loadAdminSettingsSnapshot();
        boolean orderingEnabled = settings.orderingEnabled();
        LocalDateTime expiredAt = customer.expiredAt();
        int remainingMeals = customer.remainingMeals();
        int remainingValidityDays = remainingValidityDays(expiredAt);
        String packageAlertCode = resolvePackageAlertCode(
            remainingMeals,
            expiredAt,
            settings.packageExpiryReminderDays(),
            settings.packageLowBalanceThreshold()
        );
        boolean mealReminderPopupEnabled = shouldShowMealReminderPopup(settings.mealReminderPopupEnabled(), packageAlertCode);
        return new MobileHomeResponse(
            customer.id(),
            customer.name(),
            customer.phone(),
            customer.packageName(),
            customer.totalMeals(),
            remainingMeals,
            formatDate(expiredAt),
            remainingValidityDays,
            packageAlertCode,
            resolvePackageAlertMessage(packageAlertCode, remainingMeals, remainingValidityDays),
            orderingEnabled,
            orderingEnabled ? "可下单" : "暂停接单",
            settings.holidayNoticeTitle(),
            settings.holidayNoticeDesc(),
            safeString(customer.defaultAddress()),
            safeString(customer.defaultUserRemark()),
            safeString(customer.merchantRemark()),
            parseBannerImages(settings.bannerImages()),
            resolveBannerIntervalMs(settings.bannerIntervalSeconds()),
            settings.popupAnnouncementEnabled(),
            settings.popupAnnouncementContent(),
            mealReminderPopupEnabled,
            mealReminderPopupEnabled ? buildMealReminderTitle(packageAlertCode) : "",
            mealReminderPopupEnabled ? buildMealReminderMessage(packageAlertCode, remainingMeals, remainingValidityDays) : "",
            mealReminderPopupEnabled ? buildMealReminderKey(packageAlertCode, remainingMeals, expiredAt) : ""
        );
    }

    public PageResponse<MobileMenuItemResponse> publishedMenus(String serveDate) {
        List<MobileMenuItemResponse> items = jdbcTemplate.query("""
            SELECT
                mwi.id,
                mwi.serve_date AS serve_date,
                meal_period,
                COALESCE(dish_items_json, '[]') AS dish_items_json,
                total_calories,
                COALESCE(merchant_note, '-') AS merchant_note,
                slot_status AS status
            FROM menu_week_items mwi
            JOIN menu_weeks mw ON mw.id = mwi.week_id
            WHERE mw.status = 'PUBLISHED'
              AND mwi.slot_status = 'ACTIVE'
              AND mwi.serve_date = ?
            ORDER BY CASE WHEN meal_period = 'LUNCH' THEN 1 ELSE 2 END, mwi.id
            """, (rs, rowNum) -> new MobileMenuItemResponse(
            rs.getLong("id"),
            rs.getDate("serve_date").toLocalDate().toString(),
            rs.getString("meal_period"),
            readDishItems(rs.getString("dish_items_json")),
            (Integer) rs.getObject("total_calories"),
            rs.getString("merchant_note"),
            rs.getString("status")
        ), LocalDate.parse(serveDate));
        return PageResponse.of(items, 1, 20, items.size());
    }

    public MobileCurrentWeekResponse currentWeekMenu() {
        LocalDate monday = currentWeekMonday(LocalDate.now());
        LocalDate sunday = monday.plusDays(6);
        return new MobileCurrentWeekResponse(
            monday.toString(),
            sunday.toString(),
            buildCurrentWeekDays(monday)
        );
    }

    public MobileTomorrowMenuResponse tomorrowMenu() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<MobileMenuItemResponse> items = publishedMenus(tomorrow.toString()).items();
        MobileMenuItemResponse lunchItem = items.stream()
            .filter(item -> "LUNCH".equals(item.mealPeriod()))
            .findFirst()
            .orElse(null);
        MobileMenuItemResponse dinnerItem = items.stream()
            .filter(item -> "DINNER".equals(item.mealPeriod()))
            .findFirst()
            .orElse(null);

        AdminSettingsSnapshot settings = loadAdminSettingsSnapshot();
        boolean isOrderingEnabled = settings.orderingEnabled();
        String notice = settings.holidayNoticeDesc();

        boolean selfOrderEnabled = LocalTime.now().isBefore(LocalTime.of(23, 0));
        String selfOrderNotice = selfOrderEnabled ? "" : "当前自助下单已截止，如需加单请联系专属客服微信";

        boolean canOrder = true;
        String statusText = "";

        if (!isOrderingEnabled) {
            canOrder = false;
            statusText = notice != null && !notice.isBlank() ? notice : "当前自助下单已截止，请联系专属客服微信";
        } else if (lunchItem == null && dinnerItem == null) {
            canOrder = false;
            statusText = "明日菜单待发布或店休，暂不提供配送服务";
        } else if (!selfOrderEnabled) {
            canOrder = false;
            statusText = selfOrderNotice;
        }

        return new MobileTomorrowMenuResponse(
            tomorrow.toString(),
            selfOrderEnabled,
            selfOrderNotice,
            lunchItem,
            dinnerItem,
            canOrder,
            statusText
        );
    }

    public List<MobileWeekMenuDayResponse> weekMenus(String startDate) {
        LocalDate firstDay = LocalDate.parse(startDate);
        List<MobileWeekMenuDayResponse> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate serveDate = firstDay.plusDays(i);
            PageResponse<MobileMenuItemResponse> menus = publishedMenus(serveDate.toString());
            result.add(new MobileWeekMenuDayResponse(
                serveDate.toString(),
                weekdayLabel(serveDate),
                menus.items()
            ));
        }
        return result;
    }

    public PageResponse<MobileOrderItemResponse> customerOrders(String phone, String status) {
        return customerOrders(findCustomerIdByPhone(phone), status);
    }

    public PageResponse<MobileOrderItemResponse> customerOrders(long customerId, String status) {
        List<MobileOrderItemResponse> items = jdbcTemplate.query("""
            SELECT
                mso.id,
                do.serve_date AS serve_date,
                mso.meal_period,
                COALESCE(ms.meal_name, CASE WHEN mso.meal_period = 'LUNCH' THEN '待配置午餐' ELSE '待配置晚餐' END) AS meal_name,
                COALESCE(ms.meal_detail, '') AS meal_detail,
                COALESCE(ms.merchant_note, '-') AS merchant_note,
                COALESCE(mso.user_note, mso.note, '-') AS note,
                ca.address_line AS delivery_address,
                do.source,
                mso.status,
                COALESCE(dr.receipt_url, '') AS receipt_url,
                COALESCE(dr.receipt_note, '') AS receipt_note,
                dr.delivered_at AS delivered_at,
                CASE
                    WHEN ac.status IN ('PENDING', 'PROCESSING', 'APPROVED') THEN TRUE
                    ELSE FALSE
                END AS aftersale_open,
                COALESCE(ac.status, '') AS aftersale_status,
                COALESCE(ac.issue_type, '') AS aftersale_type,
                COALESCE(ac.admin_remark, '') AS aftersale_admin_remark,
                CASE
                    WHEN dr.visible_at IS NULL THEN FALSE
                    WHEN dr.visible_at <= CURRENT_TIMESTAMP THEN TRUE
                    ELSE FALSE
                END AS receipt_visible
            FROM customers c
            JOIN daily_orders do ON do.customer_id = c.id
            JOIN meal_slot_orders mso ON mso.daily_order_id = do.id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN menu_week_items ms ON ms.serve_date = do.serve_date
                AND ms.meal_period = mso.meal_period
                AND ms.slot_status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM menu_weeks mw2 WHERE mw2.id = ms.week_id AND mw2.status = 'PUBLISHED')
            LEFT JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
            LEFT JOIN aftersale_cases ac ON ac.id = (
                SELECT ac2.id
                FROM aftersale_cases ac2
                WHERE ac2.meal_slot_order_id = mso.id
                ORDER BY ac2.id DESC
                LIMIT 1
            )
            WHERE c.id = ?
              AND mso.visible_to_customer = TRUE
              AND (? IS NULL OR ? = '' OR mso.status = ?)
            ORDER BY do.serve_date DESC, mso.id DESC
            """, (rs, rowNum) -> new MobileOrderItemResponse(
            rs.getLong("id"),
            rs.getDate("serve_date").toLocalDate().toString(),
            rs.getString("meal_period"),
            rs.getString("meal_name"),
            rs.getString("meal_detail"),
            rs.getString("merchant_note"),
            rs.getString("note"),
            rs.getString("delivery_address"),
            rs.getString("source"),
            rs.getString("status"),
            resolveUserVisibleStatus(
                rs.getString("status"),
                rs.getString("meal_period"),
                rs.getDate("serve_date").toLocalDate(),
                timestampToLocalDateTime(rs.getTimestamp("delivered_at"))
            ),
            rs.getString("receipt_url"),
            rs.getString("receipt_note"),
            formatTimestamp(rs.getTimestamp("delivered_at")),
            rs.getBoolean("receipt_visible"),
            canChangeAddress(rs.getDate("serve_date").toLocalDate()),
            resolveChangeAddressMode(rs.getDate("serve_date").toLocalDate()),
            rs.getBoolean("aftersale_open"),
            rs.getString("aftersale_status"),
            rs.getString("aftersale_type"),
            rs.getString("aftersale_admin_remark")
        ), customerId, status, status, status);
        return PageResponse.of(items, 1, 20, items.size());
    }

    @Transactional
    public MobileCreateOrderResponse createMiniappOrder(String phone, String serveDate, String mealPeriod, String deliveryAddress, String note) {
        return createMiniappOrder(findCustomerIdByPhone(phone), serveDate, mealPeriod, deliveryAddress, note);
    }

    @Transactional
    public MobileCreateOrderResponse createMiniappOrder(long customerId, String serveDate, String mealPeriod, String deliveryAddress, String note) {
        try {
            ensureOrderingEnabled();
            ensureSelfOrderAllowed(serveDate, mealPeriod);
            requirePublishedMenu(serveDate, mealPeriod);

            LocalDate orderDate = LocalDate.parse(serveDate);
            String normalizedMealPeriod = normalizeMealPeriod(mealPeriod);

            long walletId = activeWalletId(customerId);
            int remainingMeals = remainingMealsForUpdate(walletId);
            if (remainingMeals <= 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_MEALS, "剩余餐次不足，无法下单");
            }
            long addressId = ensureCustomerAddress(customerId, deliveryAddress);
            String finalUserNote = normalizeNote(note);
            String merchantRemark = normalizeCustomerMerchantRemark(customerId);
            Long mergeTargetOrderId = findMergeTargetOrderId(customerId, orderDate, normalizedMealPeriod, addressId);
            if (mergeTargetOrderId != null) {
                ExistingOrderNoteRow existingOrder = loadExistingOrderNoteRow(mergeTargetOrderId);
                String mergedUserNote = mergeOrderNote(
                    preferredOrderNote(existingOrder.userNote(), existingOrder.note()),
                    finalUserNote
                );
                LocalDateTime mergeTime = LocalDateTime.now();
                jdbcTemplate.update(
                    """
                        UPDATE meal_slot_orders
                        SET quantity = quantity + 1,
                            note = ?,
                            user_note = ?,
                            merchant_remark = CASE
                                WHEN (merchant_remark IS NULL OR TRIM(merchant_remark) = '' OR merchant_remark = '-') AND ? <> '' THEN ?
                                ELSE merchant_remark
                            END
                        WHERE id = ?
                        """,
                    mergedUserNote,
                    mergedUserNote,
                    merchantRemark,
                    merchantRemark,
                    mergeTargetOrderId
                );
                jdbcTemplate.update(
                    "UPDATE customers SET last_order_at = ? WHERE id = ?",
                    Timestamp.valueOf(mergeTime),
                    customerId
                );
                jdbcTemplate.update("UPDATE meal_wallets SET reserved_meals = reserved_meals + 1 WHERE id = ?", walletId);
                insertWalletTransaction(walletId, "RESERVE", -1, "小程序", "用户自主下单加餐占用餐次", mergeTime, mergeTargetOrderId);
                attemptWriteOrderSnapshot(
                    mergeTargetOrderId,
                    customerId,
                    "小程序",
                    normalizeSnapshotNote(mergedUserNote),
                    null,
                    List.of(),
                    mergeTime
                );
                jdbcTemplate.execute("/* force flush */ SELECT 1");
                attemptAutoAssignPendingOrders(normalizedMealPeriod, mergeTargetOrderId, customerId);
                attemptPublishCustomerEvent("customer.order.changed", customerId, mergeTargetOrderId);
                attemptPublishCustomerEvent("customer.wallet.changed", customerId, mergeTargetOrderId);
                return new MobileCreateOrderResponse(mergeTargetOrderId, "MERGED", "RESERVED");
            }
            LocalDateTime now = LocalDateTime.now();
            long dailyOrderId = ensureDailyOrderId(customerId, orderDate, now);
            LocalDateTime snapshotTime = LocalDateTime.now();
            long mealSlotOrderId = insertAndReturnId(
                """
                    INSERT INTO meal_slot_orders (
                        daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, merchant_remark, status, source_type
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                dailyOrderId, normalizedMealPeriod, normalizedMealPeriod, 1, addressId, finalUserNote, finalUserNote, merchantRemark, "PENDING_DISPATCH", "MINIAPP"
            );
        if (mealSlotOrderId <= 0) {
            mealSlotOrderId = resolveLatestMiniappOrderId(dailyOrderId, normalizedMealPeriod, normalizedMealPeriod, addressId);
        }
            jdbcTemplate.update(
                "UPDATE daily_orders SET status = 'PENDING_DISPATCH', source = 'MINIAPP' WHERE id = ?",
                dailyOrderId
            );
            jdbcTemplate.update(
                "UPDATE customers SET last_order_at = ? WHERE id = ?",
                Timestamp.valueOf(now),
                customerId
            );
            jdbcTemplate.update("UPDATE meal_wallets SET reserved_meals = reserved_meals + 1 WHERE id = ?", walletId);
            insertWalletTransaction(walletId, "RESERVE", -1, "小程序", "用户自主下单占用餐次", now, mealSlotOrderId);
            attemptWriteOrderSnapshot(
                mealSlotOrderId,
                customerId,
                "小程序",
                finalUserNote,
                null,
                List.of(),
                snapshotTime
            );

            jdbcTemplate.execute("/* force flush */ SELECT 1");
            attemptAutoAssignPendingOrders(normalizedMealPeriod, mealSlotOrderId, customerId);
            String currentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM meal_slot_orders WHERE id = ?",
                String.class,
                mealSlotOrderId
            );

            attemptPublishCustomerEvent("customer.order.changed", customerId, mealSlotOrderId);
            attemptPublishCustomerEvent("customer.wallet.changed", customerId, mealSlotOrderId);
            return new MobileCreateOrderResponse(
                mealSlotOrderId,
                currentStatus != null ? currentStatus : "PENDING_DISPATCH",
                "RESERVED"
            );
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    @Override
    @Transactional
    public MobileDeliverySubscriptionAuthorizeResponse authorizeDeliverySubscription(long customerId, long orderId, String templateId, String acceptResult) {
        if (!isAcceptedSubscribeResult(acceptResult)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持保存已同意的订阅授权");
        }
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE mso.id = ? AND do.customer_id = ?
            """, Integer.class, orderId, customerId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应订单");
        }
        jdbcTemplate.update("""
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
            """, customerId, orderId, templateId);
        pruneOldDeliverySubscriptions();
        return new MobileDeliverySubscriptionAuthorizeResponse(orderId, "AUTHORIZED");
    }

    @Override
    @Transactional
    public MobileSubscribeMessageTestResponse sendSubscribeMessageTest(long customerId, String templateId, String acceptResult) {
        if (!isAcceptedSubscribeResult(acceptResult)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持发送已同意的订阅测试消息");
        }
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
        return new MobileSubscribeMessageTestResponse(
            "SENT",
            safeString(templateId).trim(),
            "pages/profile/index"
        );
    }

    @Override
    @Transactional
    public int sendScheduledDeliverySubscribeMessages(String mealPeriod) {
        String normalizedMealPeriod = safeString(mealPeriod).trim().toUpperCase();
        if (!"LUNCH".equals(normalizedMealPeriod) && !"DINNER".equals(normalizedMealPeriod)) {
            return 0;
        }
        return sendScheduledDeliverySubscribeMessagesInternal(normalizedMealPeriod, LocalDate.now(), LocalDateTime.now().withNano(0));
    }

    @Override
    @Transactional
    public int sendAllDeliveredPendingSubscriptions() {
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

    @Transactional
    public OrderActionResponse cancelMiniappOrder(String phone, long orderId) {
        return cancelMiniappOrder(findCustomerIdByPhone(phone), orderId);
    }

    @Transactional
    public OrderActionResponse cancelMiniappOrder(long customerId, long orderId) {
        CustomerOrderStatusRow order = jdbcTemplate.query("""
            SELECT do.serve_date, mso.status
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE mso.id = ? AND do.customer_id = ?
            """,
            ps -> {
                ps.setLong(1, orderId);
                ps.setLong(2, customerId);
            },
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new CustomerOrderStatusRow(
                    rs.getObject("serve_date", LocalDate.class),
                    rs.getString("status")
                );
            }
        );
        if (order == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该订单");
        }
        LocalDate serveDate = order.serveDate();
        String status = order.status();
        if (!MiniappCustomerCancelGuard.canCustomerCancel(LocalDateTime.now(), serveDate, status, selfOrderCutoffTime)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "该订单已过用户可取消时间，仅商家后台可处理");
        }
        OrderActionResponse result = orderPrepService.cancelOrder(orderId);
        publishCustomerEvent("customer.order.changed", customerId, orderId);
        publishCustomerEvent("customer.wallet.changed", customerId, orderId);
        return result;
    }

    @Override
    @Transactional
    public MobileCreateAfterSaleResponse createAfterSale(long customerId, long orderId, MobileCreateAfterSaleRequest request) {
        return aftersaleService.createMobileCase(customerId, orderId, request);
    }

    @Override
    @Transactional
    public OrderActionResponse deleteMiniappOrder(long customerId, long orderId) {
        int updated = jdbcTemplate.update("""
            UPDATE meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            SET mso.visible_to_customer = FALSE
            WHERE mso.id = ? AND do.customer_id = ?
              AND mso.status IN ('CANCELLED', 'REFUNDED')
            """, orderId, customerId);

        if (updated == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法删除该订单（可能状态不符或订单不存在）");
        }
        publishCustomerEvent("customer.order.changed", customerId, orderId);
        publishCustomerEvent("customer.wallet.changed", customerId, orderId);
        return new OrderActionResponse(orderId, "HIDDEN");
    }

    @Override
    public List<MobileAfterSaleItemResponse> customerAfterSales(long customerId) {
        return aftersaleService.customerCases(customerId);
    }

    public List<MobileAddressResponse> customerAddresses(String phone) {
        return customerAddresses(findCustomerIdByPhone(phone));
    }

    public List<MobileAddressResponse> customerAddresses(long customerId) {
        return jdbcTemplate.query("""
            SELECT id, contact_name, contact_phone, address_line, area_code, is_default
            FROM customer_addresses
            WHERE customer_id = ?
            ORDER BY is_default DESC, id ASC
            """, (rs, rowNum) -> new MobileAddressResponse(
            rs.getLong("id"),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            rs.getString("address_line"),
            rs.getString("area_code"),
            rs.getBoolean("is_default")
        ), customerId);
    }

    @Transactional
    public MobileAddressResponse saveCustomerAddress(
        String phone,
        String contactName,
        String contactPhone,
        String addressLine,
        String areaCode,
        boolean isDefault
    ) {
        return saveCustomerAddress(findCustomerIdByPhone(phone), contactName, contactPhone, addressLine, areaCode, isDefault);
    }

    @Transactional
    public MobileAddressResponse saveCustomerAddress(
        long customerId,
        String contactName,
        String contactPhone,
        String addressLine,
        String areaCode,
        boolean isDefault
    ) {
        ContactSnapshot contact = resolveCustomerAddressContact(customerId);
        String finalAddressLine = requireAddressLine(addressLine);
        String finalAreaCode = areaCode == null ? "" : areaCode.trim();
        if (isDefault) {
            jdbcTemplate.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?", customerId);
        }
        long addressId = insertAndReturnId(
            """
                INSERT INTO customer_addresses (customer_id, contact_name, contact_phone, address_line, area_code, is_default)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            customerId,
            contact.name(),
            contact.phone(),
            finalAddressLine,
            finalAreaCode,
            isDefault
        );
        return new MobileAddressResponse(addressId, contact.name(), contact.phone(), finalAddressLine, finalAreaCode, isDefault);
    }

    @Transactional
    public MobileDefaultAddressResponse setDefaultAddress(String phone, long addressId) {
        return setDefaultAddress(findCustomerIdByPhone(phone), addressId);
    }

    @Transactional
    public MobileDefaultAddressResponse setDefaultAddress(long customerId, long addressId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE id = ? AND customer_id = ?",
            Integer.class,
            addressId,
            customerId
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该地址");
        }
        jdbcTemplate.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?", customerId);
        jdbcTemplate.update("UPDATE customer_addresses SET is_default = TRUE WHERE id = ?", addressId);
        return new MobileDefaultAddressResponse(addressId, "DEFAULT_UPDATED");
    }

    @Override
    @Transactional
    public MobileOrderAddressChangeResponse changeCustomerOrderAddress(long customerId, long orderId, long addressId) {
        CustomerOrderAddressRow order = jdbcTemplate.query("""
            SELECT mso.address_id, do.serve_date
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE mso.id = ? AND do.customer_id = ?
            """,
            ps -> {
                ps.setLong(1, orderId);
                ps.setLong(2, customerId);
            },
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new CustomerOrderAddressRow(
                    rs.getLong("address_id"),
                    rs.getObject("serve_date", LocalDate.class)
                );
            }
        );
        if (order == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该订单");
        }
        LocalDate serveDate = order.serveDate();
        if (!canChangeAddress(serveDate)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "送餐当天请联系客服修改地址");
        }
        Integer addressCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE id = ? AND customer_id = ?",
            Integer.class,
            addressId,
            customerId
        );
        if (addressCount == null || addressCount == 0) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND, "未找到该地址");
        }
        jdbcTemplate.update("UPDATE meal_slot_orders SET address_id = ? WHERE id = ?", addressId, orderId);
        publishCustomerEvent("customer.order.changed", customerId, orderId);
        return new MobileOrderAddressChangeResponse(orderId, addressId, "ADDRESS_UPDATED");
    }

    public PageResponse<WalletTransactionResponse> walletTransactions(String phone) {
        return walletTransactions(findCustomerIdByPhone(phone));
    }

    public PageResponse<WalletTransactionResponse> walletTransactions(long customerId) {
        List<WalletTransactionResponse> items = jdbcTemplate.query("""
            SELECT
                wt.id,
                mw.customer_id,
                wt.transaction_type,
                wt.meal_delta,
                wt.operator_name,
                COALESCE(wt.remark, '') AS remark,
                wt.related_order_id,
                wt.related_aftersale_id,
                wt.related_transaction_id,
                wt.refunded,
                COALESCE(wt.refund_reason_code, '') AS refund_reason_code,
                COALESCE(wt.refund_reason_text, '') AS refund_reason_text,
                wt.created_at AS created_at
            FROM wallet_transactions wt
            JOIN meal_wallets mw ON mw.id = wt.wallet_id
            WHERE mw.customer_id = ?
            ORDER BY wt.id DESC
            """, (rs, rowNum) -> new WalletTransactionResponse(
            rs.getLong("id"),
            rs.getLong("customer_id"),
            rs.getString("transaction_type"),
            rs.getInt("meal_delta"),
            rs.getString("operator_name"),
            rs.getString("remark"),
            (Long) rs.getObject("related_order_id"),
            (Long) rs.getObject("related_aftersale_id"),
            (Long) rs.getObject("related_transaction_id"),
            rs.getBoolean("refunded"),
            rs.getString("refund_reason_code"),
            rs.getString("refund_reason_text"),
            formatTimestamp(rs.getTimestamp("created_at"))
        ), customerId);
        return PageResponse.of(items, 1, 20, items.size());
    }

    private String formatTimestamp(Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private LocalDateTime timestampToLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String resolveUserVisibleStatus(String actualStatus, String mealPeriod, LocalDate serveDate, LocalDateTime deliveredAt) {
        if (!"DELIVERED".equals(actualStatus) || serveDate == null || deliveredAt == null) {
            return actualStatus;
        }
        LocalDateTime gate = resolveDeliveryNotifyThreshold(serveDate, mealPeriod);
        return LocalDateTime.now().isBefore(gate) ? "PENDING_DISPATCH" : "DELIVERED";
    }

    private LocalDateTime resolveDeliveryNotifyThreshold(LocalDate serveDate, String mealPeriod) {
        LocalTime cutoff = "DINNER".equalsIgnoreCase(mealPeriod) ? LocalTime.of(17, 0) : LocalTime.of(11, 30);
        return LocalDateTime.of(serveDate, cutoff);
    }

    private boolean canChangeAddress(LocalDate serveDate) {
        return serveDate != null && serveDate.isAfter(LocalDate.now());
    }

    private String resolveChangeAddressMode(LocalDate serveDate) {
        if (serveDate == null) {
            return "LOCKED";
        }
        LocalDate today = LocalDate.now();
        if (serveDate.isAfter(today)) {
            return "SELF_SERVICE";
        }
        if (serveDate.isEqual(today)) {
            return "CONTACT_SUPPORT";
        }
        return "LOCKED";
    }

    public PageResponse<RiderTaskItemResponse> riderTasks(String riderName) {
        return riderQueueSupport.riderTasks(riderName);
    }

    public RiderBatchSummaryResponse riderSummary(String riderName, String serveDate) {
        return riderQueueSupport.riderSummary(riderName, serveDate);
    }

    public PageResponse<RiderQueueItemResponse> riderQueue(String riderName, String serveDate) {
        return riderQueueSupport.riderQueue(riderName, serveDate);
    }

    public RiderQueueItemResponse riderQueueItem(long queueItemId, String riderName, String serveDate, Long mealSlotOrderId) {
        return riderQueueSupport.riderQueueItem(queueItemId, riderName, serveDate, mealSlotOrderId);
    }

    public RiderAddressReferenceResponse riderAddressReference(String riderName, long addressId) {
        if (isBlank(riderName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "骑手姓名不能为空");
        }
        if (addressId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "地址不能为空");
        }
        List<RiderAddressReferenceResponse> results = jdbcTemplate.query("""
            SELECT customer_address_id, reference_image_url
            FROM address_reference_images
            WHERE customer_address_id = ?
            """, (rs, rowNum) -> new RiderAddressReferenceResponse(
            rs.getLong("customer_address_id"),
            rs.getString("reference_image_url")
        ), addressId);
        if (results.isEmpty()) {
            return new RiderAddressReferenceResponse(addressId, "");
        }
        return results.get(0);
    }

    @Transactional
    public RiderAddressReferenceBatchSaveResponse saveBatchAddressReferenceImage(String riderName, RiderBatchAddressReferenceRequest request) {
        if (isBlank(riderName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "骑手姓名不能为空");
        }
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
        for (Long addressId : uniqueAddressIds) {
            upsertAddressReferenceImage(addressId, riderReceiptStorageSupport.buildReceiptUrl(request.referenceImageUrl()), null, riderName);
            updatedCount++;
        }
        return new RiderAddressReferenceBatchSaveResponse(updatedCount, new ArrayList<>(uniqueAddressIds));
    }

    @Transactional
    public RiderAddressReferenceReplaceResponse replaceAddressReferenceImage(String riderName, long addressId, String referenceImageUrl) {
        if (isBlank(riderName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "骑手姓名不能为空");
        }
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

    @Override
    public RiderDeliveryUploadResponse uploadRiderReceipt(String riderName, MultipartFile file) {
        return riderReceiptStorageSupport.uploadRiderReceipt(riderName, file);
    }

    @Transactional
    public DeliveryReceiptRecordResponse submitRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt) {
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
        jdbcTemplate.update("""
            UPDATE dispatch_batch_items
            SET item_status = 'DELIVERED'
            WHERE meal_slot_order_id = ?
            """, mealSlotOrderId);
        riderQueueSupport.refreshQueueStateForOrder(mealSlotOrderId);
        try {
            Long addressId = findAddressIdByMealSlotOrderId(mealSlotOrderId);
            saveAddressReferenceImageIfAbsent(addressId == null ? 0L : addressId, mealSlotOrderId, finalReceiptUrl, riderName);
        } catch (Exception ex) {
            // Keep receipt submission successful even if reference-image auto-save fails.
        }
        try {
            sendDeliverySubscriptionAfterReceiptIfNeeded(mealSlotOrderId, deliveredDateTime);
        } catch (Exception ex) {
            // Keep receipt submission successful even if notification delivery fails.
        }
        publishCustomerOrderChanged(mealSlotOrderId);
        publishRiderEvent("dispatch.receipt.changed", riderName, mealSlotOrderId);
        return result;
    }

    @Transactional
    public DeliveryReceiptRecordResponse updateRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt) {
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

        // 更新回执记录
        jdbcTemplate.update("""
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
        publishCustomerOrderChanged(mealSlotOrderId);
        publishRiderEvent("dispatch.receipt.changed", riderName, mealSlotOrderId);

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

    @Transactional
    public DeliveryReceiptDeleteResponse deleteRiderReceiptImage(long mealSlotOrderId, String riderName) {
        requireRiderReceiptTask(mealSlotOrderId, riderName, false, "未找到该配送任务");
        String previousReceiptUrl = requireExistingReceiptUrl(mealSlotOrderId);

        // 清空照片URL，但保留回执记录
        jdbcTemplate.update("""
            UPDATE delivery_receipts
            SET receipt_url = '',
                visible_to_customer = FALSE
            WHERE meal_slot_order_id = ?
            """, mealSlotOrderId);
        riderReceiptStorageSupport.deleteManagedReceiptFileQuietly(previousReceiptUrl, "");
        publishCustomerOrderChanged(mealSlotOrderId);
        publishRiderEvent("dispatch.receipt.changed", riderName, mealSlotOrderId);

        return new DeliveryReceiptDeleteResponse(mealSlotOrderId, "DELIVERED", "", true);
    }

    @Transactional
    public RiderQueueReorderResponse reorderRiderQueue(String riderName, List<Long> batchItemIds) {
        return riderQueueSupport.reorderRiderQueue(riderName, batchItemIds);
    }

    @Transactional
    public RiderQueueItemActionResponse deferRiderQueueItem(String riderName, long batchItemId) {
        return riderQueueSupport.deferRiderQueueItem(riderName, batchItemId);
    }

    @Transactional
    public RiderQueueItemActionResponse resumeRiderQueueItem(String riderName, long batchItemId) {
        return riderQueueSupport.resumeRiderQueueItem(riderName, batchItemId);
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

    private void publishCustomerEvent(String eventType, long customerId, Object orderId) {
        RealtimeEvent.Builder builder = RealtimeEvent.builder(eventType)
            .audience("admin")
            .audience("customer:id:" + customerId)
            .payload("customerId", customerId);
        if (orderId != null) {
            builder.payload("orderId", orderId);
        }
        realtimeEventPublisher.publish(builder.build());
    }

    private void attemptAutoAssignPendingOrders(String mealPeriod, long orderId, long customerId) {
        try {
            dispatchService.autoAssignPendingOrders(mealPeriod);
        } catch (RuntimeException ex) {
            log.warn(
                "miniapp create order auto-assign skipped customerId={} orderId={} mealPeriod={} reason={}",
                customerId,
                orderId,
                mealPeriod,
                ex.getMessage(),
                ex
            );
        }
    }

    private void attemptPublishCustomerEvent(String eventType, long customerId, Object orderId) {
        try {
            publishCustomerEvent(eventType, customerId, orderId);
        } catch (RuntimeException ex) {
            log.warn(
                "miniapp create order realtime publish skipped eventType={} customerId={} orderId={} reason={}",
                eventType,
                customerId,
                orderId,
                ex.getMessage(),
                ex
            );
        }
    }

    private void attemptWriteOrderSnapshot(
        long mealSlotOrderId,
        long customerId,
        String operatorName,
        String orderUserNote,
        String subscriptionDefaultNote,
        List<String> orderOnceMerchantNotes,
        LocalDateTime snapshotTime
    ) {
        try {
            orderNoteSnapshotService.writeOrderSnapshot(
                mealSlotOrderId,
                customerId,
                operatorName,
                orderUserNote,
                subscriptionDefaultNote,
                orderOnceMerchantNotes,
                snapshotTime
            );
        } catch (RuntimeException ex) {
            log.warn(
                "miniapp create order snapshot skipped customerId={} orderId={} reason={}",
                customerId,
                mealSlotOrderId,
                ex.getMessage(),
                ex
            );
        }
    }

    private void sendDeliverySubscriptionAfterReceiptIfNeeded(long mealSlotOrderId, LocalDateTime deliveredDateTime) {
        if (isDeliverySubscribeFixedTimeEnabled()) {
            return;
        }
        trySendDeliverySubscription(mealSlotOrderId, deliveredDateTime);
    }

    private int sendScheduledDeliverySubscribeMessagesInternal(String mealPeriod, LocalDate serveDate, LocalDateTime now) {
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
                COALESCE(c.current_openid, c.openid, '') AS current_openid,
                COALESCE(ca.address_line, '') AS address_line
            FROM customer_delivery_subscriptions cds
            JOIN meal_slot_orders mso ON mso.id = cds.meal_slot_order_id
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            WHERE cds.meal_slot_order_id = ?
              AND cds.status IN ('AUTHORIZED', 'FAILED')
            """,
            ps -> ps.setLong(1, mealSlotOrderId),
            rs -> rs.next()
                ? new DeliverySubscriptionSendContext(
                    rs.getLong("id"),
                    rs.getString("current_openid"),
                    rs.getString("address_line")
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
                    rs.getString("current_openid"),
                    ""
                )
                : null
        );
    }

    private DeliveryMealSlotContext findDeliveryMealSlotContext(long mealSlotOrderId) {
        return jdbcTemplate.query(
            """
            SELECT do.serve_date, mso.meal_period
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE mso.id = ?
            """,
            ps -> ps.setLong(1, mealSlotOrderId),
            rs -> rs.next()
                ? new DeliveryMealSlotContext(
                    rs.getDate("serve_date").toLocalDate(),
                    rs.getString("meal_period")
                )
                : null
        );
    }

    private boolean hasReachedDeliveryNotifyCutoff(String mealPeriod, LocalDate serveDate, LocalDateTime now) {
        if (serveDate == null || mealPeriod == null || now == null) {
            return false;
        }
        return !now.isBefore(resolveDeliveryNotifyThreshold(serveDate, mealPeriod));
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

    private boolean isAcceptedSubscribeResult(String acceptResult) {
        String normalized = safeString(acceptResult).trim();
        return "accept".equalsIgnoreCase(normalized)
            || "acceptWithAudio".equalsIgnoreCase(normalized)
            || "acceptWithAlert".equalsIgnoreCase(normalized);
    }

    private AdminSettingsSnapshot loadAdminSettingsSnapshot() {
        return jdbcTemplate.query(
            """
                SELECT ordering_enabled,
                       COALESCE(holiday_notice_title, '') AS holiday_notice_title,
                       COALESCE(holiday_notice_desc, '') AS holiday_notice_desc,
                       banner_images,
                       banner_interval_seconds,
                       COALESCE(package_expiry_reminder_days, 7) AS package_expiry_reminder_days,
                       COALESCE(package_low_balance_threshold, 3) AS package_low_balance_threshold,
                       popup_announcement_enabled,
                       COALESCE(popup_announcement_content, '') AS popup_announcement_content,
                       meal_reminder_popup_enabled
                FROM admin_settings
                WHERE id = 1
                """,
            rs -> rs.next()
                ? new AdminSettingsSnapshot(
                    rs.getBoolean("ordering_enabled"),
                    rs.getString("holiday_notice_title"),
                    rs.getString("holiday_notice_desc"),
                    rs.getString("banner_images"),
                    rs.getObject("banner_interval_seconds"),
                    rs.getInt("package_expiry_reminder_days"),
                    rs.getInt("package_low_balance_threshold"),
                    rs.getBoolean("popup_announcement_enabled"),
                    rs.getString("popup_announcement_content"),
                    rs.getBoolean("meal_reminder_popup_enabled")
                )
                : new AdminSettingsSnapshot(false, "", "", null, null, 7, 3, false, "", false)
        );
    }

    private MealSlotContext loadMealSlotContext(long mealSlotOrderId) {
        return jdbcTemplate.query(
            """
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

    private ExistingOrderNoteRow loadExistingOrderNoteRow(long orderId) {
        return jdbcTemplate.query(
            """
                SELECT COALESCE(note, '-') AS note,
                       COALESCE(user_note, '-') AS user_note
                FROM meal_slot_orders
                WHERE id = ?
                """,
            ps -> ps.setLong(1, orderId),
            rs -> {
                if (!rs.next()) {
                    throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "未找到对应订单");
                }
                return new ExistingOrderNoteRow(rs.getString("note"), rs.getString("user_note"));
            }
        );
    }

    private record DeliverySubscriptionSendContext(
        long id,
        String openid,
        String addressLine
    ) {
    }

    private record DeliveryMealSlotContext(
        LocalDate serveDate,
        String mealPeriod
    ) {
    }

    private record ContactSnapshot(
        String name,
        String phone
    ) {
    }

    private record AdminSettingsSnapshot(
        boolean orderingEnabled,
        String holidayNoticeTitle,
        String holidayNoticeDesc,
        String bannerImages,
        Object bannerIntervalSeconds,
        int packageExpiryReminderDays,
        int packageLowBalanceThreshold,
        boolean popupAnnouncementEnabled,
        String popupAnnouncementContent,
        boolean mealReminderPopupEnabled
    ) {
    }

    private record CustomerHomeSnapshot(
        long id,
        String name,
        String phone,
        String packageName,
        int totalMeals,
        int remainingMeals,
        LocalDateTime expiredAt,
        String merchantRemark,
        String defaultAddress,
        String defaultUserRemark
    ) {
    }

    private record MealSlotContext(LocalDate serveDate, String mealPeriod) {
    }

    private record CustomerContactRow(String name, String phone) {
    }

    private record CurrentWeekMenuRow(
        long id,
        LocalDate serveDate,
        String mealPeriod,
        String slotStatus,
        String dishItemsJson,
        Integer totalCalories,
        String merchantNote
    ) {
    }

    private record ExistingOrderNoteRow(String note, String userNote) {
    }

    private record CustomerOrderStatusRow(LocalDate serveDate, String status) {
    }

    private record CustomerOrderAddressRow(long addressId, LocalDate serveDate) {
    }

    private long findCustomerIdByPhone(String phone) {
        Long customerId = jdbcTemplate.query(
            "SELECT id FROM customers WHERE phone = ? AND active = TRUE",
            ps -> ps.setString(1, phone),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应客户");
        }
        return customerId;
    }

    private ContactSnapshot resolveCustomerAddressContact(long customerId) {
        CustomerContactRow customer = jdbcTemplate.query(
            "SELECT name, phone FROM customers WHERE id = ? AND active = TRUE",
            ps -> ps.setLong(1, customerId),
            rs -> {
                if (!rs.next()) {
                    throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应客户");
                }
                return new CustomerContactRow(rs.getString("name"), rs.getString("phone"));
            }
        );
        String finalName = safeString(customer.name()).trim();
        String finalPhone = safeString(customer.phone()).replaceAll("\\D", "");
        if (finalName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先完善姓名");
        }
        if (!finalPhone.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先完善手机号");
        }
        return new ContactSnapshot(finalName, finalPhone);
    }

    private String requireAddressLine(String addressLine) {
        String value = addressLine == null ? "" : addressLine.trim();
        if (value.length() < 4) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "详细地址至少 4 个字");
        }
        if (value.length() > 120) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "详细地址不能超过120个字");
        }
        return value;
    }

    private void ensureOrderingEnabled() {
        Boolean enabled = jdbcTemplate.queryForObject(
            "SELECT ordering_enabled FROM admin_settings WHERE id = 1",
            Boolean.class
        );
        if (!Boolean.TRUE.equals(enabled)) {
            throw new BusinessException(ErrorCode.ORDERING_DISABLED, "当前暂停接单");
        }
    }

    private void requirePublishedMenu(String serveDate, String mealPeriod) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM menu_week_items mwi
                JOIN menu_weeks mw ON mw.id = mwi.week_id
                WHERE mwi.serve_date = ?
                  AND mwi.meal_period = ?
                  AND mwi.slot_status = 'ACTIVE'
                  AND mw.status = 'PUBLISHED'
                """,
            Integer.class,
            LocalDate.parse(serveDate),
            mealPeriod
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND, "当前餐次未配置可售菜品");
        }
    }

    private void ensureSelfOrderAllowed(String serveDate, String mealPeriod) {
        LocalDate targetDate = LocalDate.parse(serveDate);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        if (!targetDate.equals(tomorrow)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "当前仅支持预订明日餐食");
        }
        if (!LocalTime.now().isBefore(selfOrderCutoffTime)) {
            throw new BusinessException(ErrorCode.ORDERING_DISABLED, "当前自助下单已截止，如需加单请联系专属客服微信");
        }
        requirePublishedMenu(serveDate, mealPeriod);
    }

    private List<MobileCurrentWeekDayResponse> buildCurrentWeekDays(LocalDate monday) {
        Long weekId = findPublishedWeekId(monday);
        List<MobileCurrentWeekDayResponse> days = new ArrayList<>();
        if (weekId == null) {
            for (int i = 0; i < 7; i++) {
                LocalDate serveDate = monday.plusDays(i);
                String slotStatus = serveDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? "REST" : "UNCONFIGURED";
                days.add(new MobileCurrentWeekDayResponse(
                    serveDate.toString(),
                    weekdayLabel(serveDate),
                    slotStatus,
                    List.of()
                ));
            }
            return days;
        }
        Map<LocalDate, List<CurrentWeekMenuRow>> grouped = new HashMap<>();
        jdbcTemplate.query(
            """
                SELECT
                    mwi.id,
                    mwi.serve_date,
                    mwi.meal_period,
                    mwi.slot_status,
                    COALESCE(mwi.dish_items_json, '[]') AS dish_items_json,
                    mwi.total_calories,
                    COALESCE(mwi.merchant_note, '-') AS merchant_note
                FROM menu_week_items mwi
                WHERE mwi.week_id = ?
                ORDER BY mwi.serve_date, CASE WHEN mwi.meal_period = 'LUNCH' THEN 1 ELSE 2 END
                """,
            ps -> ps.setLong(1, weekId),
            rs -> {
                while (rs.next()) {
                    LocalDate serveDate = rs.getDate("serve_date").toLocalDate();
                    Object totalCalories = rs.getObject("total_calories");
                    grouped.computeIfAbsent(serveDate, key -> new ArrayList<>()).add(new CurrentWeekMenuRow(
                        rs.getLong("id"),
                        serveDate,
                        rs.getString("meal_period"),
                        rs.getString("slot_status"),
                        rs.getString("dish_items_json"),
                        totalCalories == null ? null : ((Number) totalCalories).intValue(),
                        rs.getString("merchant_note")
                    ));
                }
                return null;
            }
        );
        for (int i = 0; i < 7; i++) {
            LocalDate serveDate = monday.plusDays(i);
            List<CurrentWeekMenuRow> rows = grouped.getOrDefault(serveDate, List.of());
            String slotStatus = summarizeDayStatus(rows, serveDate);
            List<MobileMenuItemResponse> items = rows.stream()
                .filter(row -> "ACTIVE".equals(row.slotStatus()))
                .map(row -> new MobileMenuItemResponse(
                    row.id(),
                    serveDate.toString(),
                    row.mealPeriod(),
                    readDishItems(row.dishItemsJson()),
                    row.totalCalories(),
                    row.merchantNote(),
                    "ACTIVE"
                ))
                .toList();
            days.add(new MobileCurrentWeekDayResponse(
                serveDate.toString(),
                weekdayLabel(serveDate),
                slotStatus,
                items
            ));
        }
        return days;
    }

    private Long findPublishedWeekId(LocalDate monday) {
        return jdbcTemplate.query(
            "SELECT id FROM menu_weeks WHERE week_start_date = ? AND status = 'PUBLISHED' ORDER BY id DESC LIMIT 1",
            ps -> ps.setObject(1, monday),
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private LocalDate currentWeekMonday(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    private String summarizeDayStatus(List<CurrentWeekMenuRow> rows, LocalDate serveDate) {
        if (rows.isEmpty()) {
            return serveDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? "REST" : "UNCONFIGURED";
        }
        boolean hasActive = rows.stream().anyMatch(row -> "ACTIVE".equals(row.slotStatus()));
        if (hasActive) {
            return "ACTIVE";
        }
        boolean allRest = rows.stream().allMatch(row -> "REST".equals(row.slotStatus()));
        return allRest ? "REST" : "UNCONFIGURED";
    }

    private long activeWalletId(long customerId) {
        Long walletId = jdbcTemplate.query(
            "SELECT id FROM meal_wallets WHERE customer_id = ? AND active = TRUE AND (expired_at IS NULL OR expired_at >= CURRENT_TIMESTAMP)",
            ps -> ps.setLong(1, customerId),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (walletId == null) {
            throw new BusinessException(ErrorCode.WALLET_BALANCE_NOT_ENOUGH, "客户未开通有效套餐");
        }
        return walletId;
    }

    private int remainingMeals(long walletId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT total_meals - reserved_meals - consumed_meals FROM meal_wallets WHERE id = ?",
            Integer.class,
            walletId
        );
        return count == null ? 0 : count;
    }

    private int remainingValidityDays(LocalDateTime expiredAt) {
        if (expiredAt == null) {
            return 0;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiredAt.toLocalDate());
    }

    private int intSetting(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "" : value.toLocalDate().toString();
    }

    private String resolvePackageAlertCode(int remainingMeals, LocalDateTime expiredAt, int expiryReminderDays, int lowBalanceThreshold) {
        int remainingDays = remainingValidityDays(expiredAt);
        if (expiredAt != null && remainingDays < 0) {
            return "EXPIRED";
        }
        if (expiredAt != null && remainingDays <= expiryReminderDays) {
            return "EXPIRING_SOON";
        }
        if (remainingMeals <= lowBalanceThreshold) {
            return "LOW_BALANCE";
        }
        return "";
    }

    private String resolvePackageAlertMessage(String alertCode, int remainingMeals, int remainingValidityDays) {
        return switch (alertCode) {
            case "EXPIRED" -> "餐包已到期，建议尽快联系商家续卡";
            case "EXPIRING_SOON" -> "餐包还有 " + Math.max(0, remainingValidityDays) + " 天到期，建议优先安排近期餐食或联系商家续卡";
            case "LOW_BALANCE" -> "当前仅剩 " + Math.max(0, remainingMeals) + " 餐，建议提前联系商家续卡或补餐";
            default -> "";
        };
    }

    private boolean shouldShowMealReminderPopup(Object settingValue, String alertCode) {
        return Boolean.TRUE.equals(settingValue) && !isBlank(alertCode);
    }

    private String buildMealReminderTitle(String alertCode) {
        return switch (alertCode) {
            case "EXPIRED" -> "餐包已到期，建议尽快续卡";
            case "EXPIRING_SOON" -> "餐包即将到期，记得提前安排";
            case "LOW_BALANCE" -> "餐次余额不多了，建议提前补足";
            default -> "";
        };
    }

    private String buildMealReminderMessage(String alertCode, int remainingMeals, int remainingValidityDays) {
        return switch (alertCode) {
            case "EXPIRED" -> "你的餐包已到期，建议尽快联系商家续卡；如果还想继续按时用餐，也可以提前和商家确认后续安排。";
            case "EXPIRING_SOON" -> "当前餐包还有 " + Math.max(0, remainingValidityDays) + " 天到期，建议优先安排近期餐食或联系商家续卡，避免影响接下来的配送。";
            case "LOW_BALANCE" -> "当前仅剩 " + Math.max(0, remainingMeals) + " 餐，建议提前联系商家续卡或补餐，避免临近用餐时余额不足。";
            default -> "";
        };
    }

    private String buildMealReminderKey(String alertCode, int remainingMeals, LocalDateTime expiredAt) {
        if (isBlank(alertCode)) {
            return "";
        }
        return alertCode + "|" + remainingMeals + "|" + formatDate(expiredAt);
    }

    private int remainingMealsForUpdate(long walletId) {
        Integer count = jdbcTemplate.query(
            "SELECT total_meals - reserved_meals - consumed_meals AS remaining FROM meal_wallets WHERE id = ? FOR UPDATE",
            ps -> ps.setLong(1, walletId),
            rs -> rs.next() ? rs.getInt("remaining") : null
        );
        return count == null ? 0 : count;
    }

    private Long findMergeTargetOrderId(long customerId, LocalDate serveDate, String mealPeriod, long addressId) {
        return jdbcTemplate.query(
            """
                SELECT mso.id
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE do.customer_id = ?
                  AND do.serve_date = ?
                  AND mso.meal_period = ?
                  AND mso.address_id = ?
                  AND mso.status NOT IN ('CANCELLED', 'REFUNDED')
                  AND NOT EXISTS (
                      SELECT 1
                      FROM aftersale_cases ac
                      WHERE ac.meal_slot_order_id = mso.id
                        AND ac.issue_type = 'REFUND'
                        AND ac.status IN ('PENDING', 'PROCESSING', 'APPROVED')
                  )
                ORDER BY mso.id DESC
                LIMIT 1
                """,
            ps -> {
                ps.setLong(1, customerId);
                ps.setObject(2, serveDate);
                ps.setString(3, mealPeriod);
                ps.setLong(4, addressId);
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private long ensureCustomerAddress(long customerId, String deliveryAddress) {
        if (!isBlank(deliveryAddress)) {
            List<Long> existingIds = jdbcTemplate.queryForList(
                "SELECT id FROM customer_addresses WHERE customer_id = ? AND address_line = ?",
                Long.class,
                customerId,
                deliveryAddress
            );
            if (!existingIds.isEmpty()) {
                return existingIds.get(0);
            }
        CustomerContactRow customer = jdbcTemplate.query(
            "SELECT name, phone FROM customers WHERE id = ?",
            ps -> ps.setLong(1, customerId),
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new CustomerContactRow(rs.getString("name"), rs.getString("phone"));
            }
        );
        if (customer == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应客户");
        }
            long insertedAddressId = insertAndReturnId(
                "INSERT INTO customer_addresses (customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (?, ?, ?, ?, ?, FALSE)",
                customerId,
                safeString(customer.name()),
                safeString(customer.phone()),
                deliveryAddress,
                deliveryAddress.contains("高新区") ? "高新区" : "老城区"
            );
            if (insertedAddressId > 0) {
                return insertedAddressId;
            }
            return resolveLatestCustomerAddressId(customerId, deliveryAddress);
        }
        Long defaultAddressId = jdbcTemplate.query(
            "SELECT id FROM customer_addresses WHERE customer_id = ? ORDER BY is_default DESC, id ASC",
            ps -> ps.setLong(1, customerId),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (defaultAddressId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户没有可用地址");
        }
        return defaultAddressId;
    }

    private void insertWalletTransaction(long walletId, String transactionType, int mealDelta, String operatorName, String remark, LocalDateTime createdAt, Long relatedOrderId) {
        jdbcTemplate.update(
            "INSERT INTO wallet_transactions (wallet_id, transaction_type, meal_delta, operator_name, remark, created_at, related_order_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
            walletId,
            transactionType,
            mealDelta,
            operatorName,
            remark,
            Timestamp.valueOf(createdAt),
            relatedOrderId
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
        if (key != null) {
            return key.longValue();
        }
        return 0L;
    }

    private long resolveLatestDailyOrderId(long customerId, LocalDate serveDate) {
        Long resolvedId = findExistingDailyOrderId(customerId, serveDate);
        if (resolvedId == null || resolvedId <= 0) {
            throw new IllegalStateException("无法定位刚创建的日订单");
        }
        return resolvedId;
    }

    private Long findExistingDailyOrderId(long customerId, LocalDate serveDate) {
        return jdbcTemplate.query(
            """
                SELECT id
                FROM daily_orders
                WHERE customer_id = ? AND serve_date = ?
                ORDER BY id DESC
                LIMIT 1
                """,
            ps -> {
                ps.setLong(1, customerId);
                ps.setObject(2, serveDate);
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private long ensureDailyOrderId(long customerId, LocalDate orderDate, LocalDateTime now) {
        Long existingDailyOrderId = findExistingDailyOrderId(customerId, orderDate);
        if (existingDailyOrderId != null && existingDailyOrderId > 0) {
            return existingDailyOrderId;
        }
        try {
            long insertedId = insertAndReturnId(
                "INSERT INTO daily_orders (customer_id, serve_date, source, status, locked, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                customerId,
                orderDate,
                "MINIAPP",
                "PENDING_DISPATCH",
                false,
                Timestamp.valueOf(now)
            );
            if (insertedId > 0) {
                return insertedId;
            }
        } catch (DataIntegrityViolationException ex) {
            log.info(
                "miniapp daily order reused after unique-key race customerId={} serveDate={} reason={}",
                customerId,
                orderDate,
                ex.getMessage()
            );
        }
        return resolveLatestDailyOrderId(customerId, orderDate);
    }

    private long resolveLatestMiniappOrderId(long dailyOrderId, String mealPeriod, String deliveryMealPeriod, long addressId) {
        Long resolvedId = jdbcTemplate.query(
            """
                SELECT id
                FROM meal_slot_orders
                WHERE daily_order_id = ?
                  AND meal_period = ?
                  AND delivery_meal_period = ?
                  AND address_id = ?
                  AND source_type = 'MINIAPP'
                ORDER BY id DESC
                LIMIT 1
                """,
            ps -> {
                ps.setLong(1, dailyOrderId);
                ps.setString(2, mealPeriod);
                ps.setString(3, deliveryMealPeriod);
                ps.setLong(4, addressId);
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (resolvedId == null || resolvedId <= 0) {
            throw new IllegalStateException("无法定位刚创建的明细订单");
        }
        return resolvedId;
    }

    private long resolveLatestCustomerAddressId(long customerId, String deliveryAddress) {
        Long resolvedId = jdbcTemplate.query(
            """
                SELECT id
                FROM customer_addresses
                WHERE customer_id = ?
                  AND address_line = ?
                ORDER BY id DESC
                LIMIT 1
                """,
            ps -> {
                ps.setLong(1, customerId);
                ps.setString(2, deliveryAddress);
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (resolvedId == null || resolvedId <= 0) {
            throw new IllegalStateException("无法定位刚创建的客户地址");
        }
        return resolvedId;
    }

    private String normalizeNote(String note) {
        return isBlank(note) ? "-" : note.trim();
    }

    private String normalizeSnapshotNote(String note) {
        String normalized = isBlank(note) ? "" : note.trim();
        return normalized.isEmpty() || "-".equals(normalized) ? null : normalized;
    }

    private String normalizeCustomerMerchantRemark(long customerId) {
        try {
            List<String> remarks = jdbcTemplate.queryForList(
                "SELECT COALESCE(merchant_remark, '') FROM customers WHERE id = ?",
                String.class,
                customerId
            );
            if (remarks.isEmpty()) {
                return "";
            }
            String normalized = remarks.get(0) == null ? "" : remarks.get(0).trim();
            return "-".equals(normalized) ? "" : normalized;
        } catch (RuntimeException ex) {
            log.warn(
                "miniapp create order merchant remark skipped customerId={} reason={}",
                customerId,
                ex.getMessage(),
                ex
            );
            return "";
        }
    }

    private String preferredOrderNote(Object userNote, Object fallbackNote) {
        String normalizedUserNote = normalizeOrderMergeNote(userNote);
        if (!normalizedUserNote.isEmpty()) {
            return normalizedUserNote;
        }
        return normalizeOrderMergeNote(fallbackNote);
    }

    private String normalizeOrderMergeNote(Object value) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isEmpty() || "-".equals(normalized) ? "" : normalized;
    }

    private String mergeOrderNote(String existingNote, String newNote) {
        String current = normalizeOrderMergeNote(existingNote);
        String incoming = normalizeOrderMergeNote(newNote);
        if (incoming.isEmpty()) {
            return current.isEmpty() ? "-" : current;
        }
        if (current.isEmpty()) {
            return incoming;
        }
        if (current.contains(incoming)) {
            return current;
        }
        return current + "；" + incoming;
    }

    private String normalizeMealPeriod(String mealPeriod) {
        if ("DINNER".equalsIgnoreCase(mealPeriod) || safeString(mealPeriod).contains("晚餐")) {
            return "DINNER";
        }
        return "LUNCH";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Long findAddressIdByMealSlotOrderId(long mealSlotOrderId) {
        List<Long> results = jdbcTemplate.query("""
            SELECT address_id
            FROM meal_slot_orders
            WHERE id = ?
            """, (rs, rowNum) -> rs.getLong("address_id"), mealSlotOrderId);
        return results.isEmpty() ? null : results.get(0);
    }

    private void saveAddressReferenceImageIfAbsent(long addressId, long orderId, String receiptUrl, String riderName) {
        if (addressId <= 0 || isBlank(receiptUrl)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM address_reference_images
            WHERE customer_address_id = ?
            """, Integer.class, addressId);
        if (count != null && count > 0) {
            return;
        }
        upsertAddressReferenceImage(addressId, receiptUrl, orderId, riderName);
    }

    private void upsertAddressReferenceImage(long addressId, String referenceImageUrl, Long sourceOrderId, String riderName) {
        jdbcTemplate.update("""
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
        Integer receiptCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM delivery_receipts
            WHERE meal_slot_order_id = ?
            """, Integer.class, mealSlotOrderId);
        if (receiptCount == null || receiptCount == 0) {
            throw new BusinessException(ErrorCode.RIDER_TASK_NOT_FOUND, "未找到回执记录");
        }
        return jdbcTemplate.queryForObject("""
            SELECT receipt_url
            FROM delivery_receipts
            WHERE meal_slot_order_id = ?
            """, String.class, mealSlotOrderId);
    }

    private LocalDateTime resolveReceiptVisibleAt(long mealSlotOrderId, LocalDateTime deliveredDateTime) {
        MealSlotContext row = loadMealSlotContext(mealSlotOrderId);
        LocalDateTime threshold = resolveDeliveryNotifyThreshold(row.serveDate(), row.mealPeriod());
        return deliveredDateTime.isBefore(threshold) ? threshold : deliveredDateTime;
    }

    private String weekdayLabel(LocalDate serveDate) {
        return switch (serveDate.getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private List<String> readDishItems(String rawJson) {
        try {
            if (rawJson == null || rawJson.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(rawJson, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("菜品列表解析失败", ex);
        }
    }

    @Override
    @Transactional
    public RiderDeliveryExceptionReportResponse reportDeliveryException(
        long mealSlotOrderId,
        String riderName,
        String exceptionType,
        String exceptionNote,
        List<String> exceptionImages
    ) {
        return riderQueueSupport.reportDeliveryException(
            mealSlotOrderId,
            riderName,
            exceptionType,
            exceptionNote,
            exceptionImages
        );
    }

    @Override
    public PageResponse<RiderTaskItemResponse> riderCompletedToday(String riderName) {
        return riderQueueSupport.riderCompletedToday(riderName);
    }

    @Override
    public RiderOrderStatusRevertResponse revertOrderStatus(long mealSlotOrderId, String riderName) {
        RiderOrderStatusRevertResponse result = extension.revertOrderStatus(mealSlotOrderId, riderName);
        publishCustomerOrderChanged(mealSlotOrderId);
        publishRiderEvent("dispatch.receipt.changed", riderName, mealSlotOrderId);
        return result;
    }

    private void publishCustomerOrderChanged(long mealSlotOrderId) {
        Long customerId = findCustomerIdByMealSlotOrderId(mealSlotOrderId);
        if (customerId == null) {
            return;
        }
        publishCustomerEvent("customer.order.changed", customerId, mealSlotOrderId);
    }

    private Long findCustomerIdByMealSlotOrderId(long mealSlotOrderId) {
        List<Long> customerIds = jdbcTemplate.query(
            """
                SELECT doo.customer_id
                FROM meal_slot_orders mso
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE mso.id = ?
                """,
            (rs, rowNum) -> rs.getLong("customer_id"),
            mealSlotOrderId
        );
        return customerIds.isEmpty() ? null : customerIds.get(0);
    }

    @Override
    public RiderOrderSequenceSaveResponse saveOrderSequence(String riderName, String mealPeriod, List<Long> batchItemIds) {
        return extension.saveOrderSequence(riderName, mealPeriod, batchItemIds);
    }

    private List<MobileBannerItemResponse> parseBannerImages(Object bannerImages) {
        if (bannerImages == null || String.valueOf(bannerImages).isBlank() || "null".equals(String.valueOf(bannerImages))) {
            return List.of(new MobileBannerItemResponse("../../assets/green-intro.jpg", true));
        }
        try {
            JsonNode root = objectMapper.readTree(String.valueOf(bannerImages));
            if (!root.isArray() || root.isEmpty()) {
                return List.of(new MobileBannerItemResponse("../../assets/green-intro.jpg", true));
            }
            List<MobileBannerItemResponse> items = new ArrayList<>();
            for (JsonNode node : root) {
                if (node == null || node.isNull()) {
                    continue;
                }
                if (node.isTextual()) {
                    String imageUrl = node.asText("").trim();
                    if (!imageUrl.isEmpty()) {
                        items.add(new MobileBannerItemResponse(imageUrl, true));
                    }
                    continue;
                }
                if (!node.isObject()) {
                    continue;
                }
                String imageUrl = node.path("imageUrl").asText("").trim();
                if (imageUrl.isEmpty()) {
                    imageUrl = node.path("url").asText("").trim();
                }
                if (imageUrl.isEmpty()) {
                    continue;
                }
                boolean enabled = !node.has("enabled") || node.path("enabled").asBoolean(true);
                if (!enabled) {
                    continue;
                }
                items.add(new MobileBannerItemResponse(imageUrl, true));
            }
            return items.isEmpty() ? List.of(new MobileBannerItemResponse("../../assets/green-intro.jpg", true)) : items;
        } catch (JsonProcessingException e) {
            return List.of(new MobileBannerItemResponse("../../assets/green-intro.jpg", true));
        }
    }

    private int resolveBannerIntervalMs(Object value) {
        int seconds;
        if (value instanceof Number number) {
            seconds = number.intValue();
        } else {
            try {
                seconds = Integer.parseInt(safeString(value).trim());
            } catch (NumberFormatException ignored) {
                seconds = 3;
            }
        }
        return Math.max(1, seconds) * 1000;
    }
}
