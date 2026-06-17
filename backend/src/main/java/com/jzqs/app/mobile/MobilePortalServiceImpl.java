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
import com.jzqs.app.delivery.service.DeliveryService;
import com.jzqs.app.mobile.api.MobileAddressResponse;
import com.jzqs.app.mobile.api.MobileAfterSaleItemResponse;
import com.jzqs.app.mobile.api.MobileBannerItemResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import com.jzqs.app.mobile.api.MobileCurrentWeekDayResponse;
import com.jzqs.app.mobile.api.MobileCurrentWeekResponse;
import com.jzqs.app.mobile.api.MobileHomeResponse;
import com.jzqs.app.mobile.api.MobileMenuItemResponse;
import com.jzqs.app.mobile.api.MobileOrderItemResponse;
import com.jzqs.app.mobile.api.MobileTomorrowMenuResponse;
import com.jzqs.app.mobile.api.MobileWeekMenuDayResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceResponse;
import com.jzqs.app.mobile.api.RiderBatchAddressReferenceRequest;
import com.jzqs.app.mobile.api.RiderBatchSummaryResponse;
import com.jzqs.app.mobile.api.RiderDeliveryUploadResponse;
import com.jzqs.app.mobile.api.RiderQueueItemResponse;
import com.jzqs.app.mobile.api.RiderTaskItemResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.jzqs.app.order.service.OrderPrepService;
import com.jzqs.app.order.service.OrderNoteSnapshotService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private static final long MAX_RECEIPT_UPLOAD_SIZE = 5L * 1024 * 1024;
    private static final String DEBUG_MINIAPP_ORDER_ENV_FILE = ".dbg/miniapp-order-500.env";
    private static final Logger log = LoggerFactory.getLogger(MobilePortalServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;
    private final OrderPrepService orderPrepService;
    private final OrderNoteSnapshotService orderNoteSnapshotService;
    private final DeliveryService deliveryService;
    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;
    private final MobilePortalServiceExtension extension;
    private final AftersaleService aftersaleService;
    private final WeChatService weChatService;
    private final TransactionalRealtimePublisher realtimeEventPublisher;
    private final Path uploadRootDir;

    public MobilePortalServiceImpl(
        JdbcTemplate jdbcTemplate,
        OrderPrepService orderPrepService,
        OrderNoteSnapshotService orderNoteSnapshotService,
        DeliveryService deliveryService,
        DispatchService dispatchService,
        ObjectMapper objectMapper,
        MobilePortalServiceExtension extension,
        AftersaleService aftersaleService,
        WeChatService weChatService,
        TransactionalRealtimePublisher realtimeEventPublisher,
        @Value("${app.upload-dir:./uploads}") String uploadDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderPrepService = orderPrepService;
        this.orderNoteSnapshotService = orderNoteSnapshotService;
        this.deliveryService = deliveryService;
        this.dispatchService = dispatchService;
        this.objectMapper = objectMapper;
        this.extension = extension;
        this.aftersaleService = aftersaleService;
        this.weChatService = weChatService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.uploadRootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public MobileHomeResponse customerHome(String phone) {
        return customerHome(findCustomerIdByPhone(phone));
    }

    public MobileHomeResponse guestHome() {
        Map<String, Object> settings = jdbcTemplate.queryForMap(
            """
                SELECT ordering_enabled,
                       holiday_notice_title,
                       holiday_notice_desc,
                       banner_images,
                       banner_interval_seconds,
                       popup_announcement_enabled,
                       popup_announcement_content,
                       meal_reminder_popup_enabled
                FROM admin_settings
                WHERE id = 1
                """
        );
        boolean orderingEnabled = Boolean.TRUE.equals(settings.get("ordering_enabled"));
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
            safeString(settings.get("holiday_notice_title")),
            safeString(settings.get("holiday_notice_desc")),
            "",
            "",
            "",
            parseBannerImages(settings.get("banner_images")),
            resolveBannerIntervalMs(settings.get("banner_interval_seconds")),
            Boolean.TRUE.equals(settings.get("popup_announcement_enabled")),
            safeString(settings.get("popup_announcement_content")),
            false,
            "",
            "",
            ""
        );
    }

    public MobileHomeResponse customerHome(long customerId) {
        Map<String, Object> customer = jdbcTemplate.query("""
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
            Map<String, Object> row = new HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("name", rs.getString("name"));
            row.put("phone", rs.getString("phone"));
            row.put("package_name", rs.getString("package_name"));
            row.put("total_meals", rs.getInt("total_meals"));
            row.put("remaining_meals", rs.getInt("remaining_meals"));
            row.put("expired_at", rs.getTimestamp("expired_at") == null ? null : rs.getTimestamp("expired_at").toLocalDateTime());
            row.put("merchant_remark", rs.getString("merchant_remark"));
            row.put("default_address", rs.getString("default_address"));
            row.put("default_user_remark", rs.getString("default_user_remark"));
            return row;
        });
        if (customer == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应客户");
        }
        Map<String, Object> settings = jdbcTemplate.queryForMap(
            """
                SELECT ordering_enabled,
                       holiday_notice_title,
                       holiday_notice_desc,
                       banner_images,
                       banner_interval_seconds,
                       package_expiry_reminder_days,
                       package_low_balance_threshold,
                       popup_announcement_enabled,
                       popup_announcement_content,
                       meal_reminder_popup_enabled
                FROM admin_settings
                WHERE id = 1
                """
        );
        boolean orderingEnabled = Boolean.TRUE.equals(settings.get("ordering_enabled"));
        LocalDateTime expiredAt = (LocalDateTime) customer.get("expired_at");
        int remainingMeals = ((Number) customer.get("remaining_meals")).intValue();
        int remainingValidityDays = remainingValidityDays(expiredAt);
        String packageAlertCode = resolvePackageAlertCode(
            remainingMeals,
            expiredAt,
            intSetting(settings.get("package_expiry_reminder_days"), 7),
            intSetting(settings.get("package_low_balance_threshold"), 3)
        );
        boolean mealReminderPopupEnabled = shouldShowMealReminderPopup(settings.get("meal_reminder_popup_enabled"), packageAlertCode);
        return new MobileHomeResponse(
            ((Number) customer.get("id")).longValue(),
            String.valueOf(customer.get("name")),
            String.valueOf(customer.get("phone")),
            String.valueOf(customer.get("package_name")),
            ((Number) customer.get("total_meals")).intValue(),
            remainingMeals,
            formatDate(expiredAt),
            remainingValidityDays,
            packageAlertCode,
            resolvePackageAlertMessage(packageAlertCode, remainingMeals, remainingValidityDays),
            orderingEnabled,
            orderingEnabled ? "可下单" : "暂停接单",
            safeString(settings.get("holiday_notice_title")),
            safeString(settings.get("holiday_notice_desc")),
            customer.get("default_address") == null ? "" : String.valueOf(customer.get("default_address")),
            customer.get("default_user_remark") == null ? "" : String.valueOf(customer.get("default_user_remark")),
            customer.get("merchant_remark") == null ? "" : String.valueOf(customer.get("merchant_remark")),
            parseBannerImages(settings.get("banner_images")),
            resolveBannerIntervalMs(settings.get("banner_interval_seconds")),
            Boolean.TRUE.equals(settings.get("popup_announcement_enabled")),
            safeString(settings.get("popup_announcement_content")),
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

        Map<String, Object> settings = jdbcTemplate.queryForMap(
            "SELECT ordering_enabled, holiday_notice_desc FROM admin_settings WHERE id = 1"
        );
        boolean isOrderingEnabled = Boolean.TRUE.equals(settings.get("ordering_enabled"));
        String notice = safeString(settings.get("holiday_notice_desc"));

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
    public Map<String, Object> createMiniappOrder(String phone, String serveDate, String mealPeriod, String deliveryAddress, String note) {
        return createMiniappOrder(findCustomerIdByPhone(phone), serveDate, mealPeriod, deliveryAddress, note);
    }

    @Transactional
    public Map<String, Object> createMiniappOrder(long customerId, String serveDate, String mealPeriod, String deliveryAddress, String note) {
        String debugTraceId = UUID.randomUUID().toString();
        try {
            // #region debug-point A:entry
            reportMiniappOrderDebug("pre-fix", "A", debugTraceId, "createMiniappOrder:entry", Map.of(
                "customerId", customerId,
                "serveDate", safeString(serveDate),
                "mealPeriod", safeString(mealPeriod),
                "deliveryAddress", safeString(deliveryAddress),
                "noteLength", safeString(note).length()
            ));
            // #endregion
            ensureOrderingEnabled();
            ensureSelfOrderAllowed(serveDate, mealPeriod);
            requirePublishedMenu(serveDate, mealPeriod);

            LocalDate orderDate = LocalDate.parse(serveDate);
            String normalizedMealPeriod = normalizeMealPeriod(mealPeriod);

            long walletId = activeWalletId(customerId);
            int remainingMeals = remainingMealsForUpdate(walletId);
            // #region debug-point B:wallet
            reportMiniappOrderDebug("pre-fix", "B", debugTraceId, "createMiniappOrder:wallet", Map.of(
                "customerId", customerId,
                "walletId", walletId,
                "remainingMeals", remainingMeals,
                "normalizedMealPeriod", normalizedMealPeriod
            ));
            // #endregion
            if (remainingMeals <= 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_MEALS, "剩余餐次不足，无法下单");
            }
            long addressId = ensureCustomerAddress(customerId, deliveryAddress);
            String finalUserNote = normalizeNote(note);
            String merchantRemark = normalizeCustomerMerchantRemark(customerId);
            Long mergeTargetOrderId = findMergeTargetOrderId(customerId, orderDate, normalizedMealPeriod, addressId);
            // #region debug-point C:address-merge
            reportMiniappOrderDebug("pre-fix", "C", debugTraceId, "createMiniappOrder:address-merge", Map.of(
                "customerId", customerId,
                "addressId", addressId,
                "mergeTargetOrderId", mergeTargetOrderId == null ? 0L : mergeTargetOrderId,
                "hasMerchantRemark", !isBlank(merchantRemark)
            ));
            // #endregion
            if (mergeTargetOrderId != null) {
                Map<String, Object> existingOrder = jdbcTemplate.queryForMap(
                    """
                        SELECT COALESCE(note, '-') AS note,
                               COALESCE(user_note, '-') AS user_note
                        FROM meal_slot_orders
                        WHERE id = ?
                        """,
                    mergeTargetOrderId
                );
                String mergedUserNote = mergeOrderNote(
                    preferredOrderNote(existingOrder.get("user_note"), existingOrder.get("note")),
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
                // #region debug-point D:merge-snapshot
                reportMiniappOrderDebug("pre-fix", "D", debugTraceId, "createMiniappOrder:merge-before-snapshot", Map.of(
                    "orderId", mergeTargetOrderId,
                    "mode", "MERGE"
                ));
                // #endregion
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
                Map<String, Object> result = Map.of(
                    "orderId", mergeTargetOrderId,
                    "status", "MERGED",
                    "walletAction", "RESERVED"
                );
                // #region debug-point E:merge-return
                reportMiniappOrderDebug("pre-fix", "E", debugTraceId, "createMiniappOrder:merge-return", result);
                // #endregion
                attemptPublishCustomerEvent("customer.order.changed", customerId, mergeTargetOrderId);
                attemptPublishCustomerEvent("customer.wallet.changed", customerId, mergeTargetOrderId);
                return result;
            }
            LocalDateTime now = LocalDateTime.now();
            Long existingDailyOrderId = jdbcTemplate.query(
                """
                    SELECT id
                    FROM daily_orders
                    WHERE customer_id = ? AND serve_date = ?
                    ORDER BY id DESC
                    LIMIT 1
                    """,
                ps -> {
                    ps.setLong(1, customerId);
                    ps.setObject(2, orderDate);
                },
                rs -> rs.next() ? rs.getLong(1) : null
            );
        long dailyOrderId;
        if (existingDailyOrderId == null) {
            dailyOrderId = insertAndReturnId(
                "INSERT INTO daily_orders (customer_id, serve_date, source, status, locked, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                customerId, orderDate, "MINIAPP", "PENDING_DISPATCH", false, Timestamp.valueOf(now)
            );
            if (dailyOrderId <= 0) {
                dailyOrderId = resolveLatestDailyOrderId(customerId, orderDate);
            }
        } else {
            dailyOrderId = existingDailyOrderId;
        }
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
            // #region debug-point D:new-order-snapshot
            reportMiniappOrderDebug("pre-fix", "D", debugTraceId, "createMiniappOrder:new-before-snapshot", Map.of(
                "dailyOrderId", dailyOrderId,
                "mealSlotOrderId", mealSlotOrderId,
                "mode", "NEW"
            ));
            // #endregion
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

            Map<String, Object> result = Map.of(
                "orderId", mealSlotOrderId,
                "status", currentStatus != null ? currentStatus : "PENDING_DISPATCH",
                "walletAction", "RESERVED"
            );
            // #region debug-point E:new-return
            reportMiniappOrderDebug("pre-fix", "E", debugTraceId, "createMiniappOrder:new-return", result);
            // #endregion
            attemptPublishCustomerEvent("customer.order.changed", customerId, mealSlotOrderId);
            attemptPublishCustomerEvent("customer.wallet.changed", customerId, mealSlotOrderId);
            return result;
        } catch (RuntimeException ex) {
            // #region debug-point F:error
            reportMiniappOrderDebug("pre-fix", "F", debugTraceId, "createMiniappOrder:error", Map.of(
                "customerId", customerId,
                "serveDate", safeString(serveDate),
                "mealPeriod", safeString(mealPeriod),
                "errorType", ex.getClass().getName(),
                "errorMessage", safeString(ex.getMessage())
            ));
            // #endregion
            throw ex;
        }
    }

    @Override
    @Transactional
    public Map<String, Object> authorizeDeliverySubscription(long customerId, long orderId, String templateId, String acceptResult) {
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
        return Map.of(
            "orderId", orderId,
            "status", "AUTHORIZED"
        );
    }

    @Override
    @Transactional
    public Map<String, Object> sendSubscribeMessageTest(long customerId, String templateId, String acceptResult) {
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
        return Map.of(
            "status", "SENT",
            "templateId", safeString(templateId).trim(),
            "page", "pages/profile/index"
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

    @Transactional
    public Map<String, Object> cancelMiniappOrder(String phone, long orderId) {
        return cancelMiniappOrder(findCustomerIdByPhone(phone), orderId);
    }

    @Transactional
    public Map<String, Object> cancelMiniappOrder(long customerId, long orderId) {
        Map<String, Object> order = jdbcTemplate.query("""
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
                Map<String, Object> row = new HashMap<>();
                row.put("serveDate", rs.getObject("serve_date", LocalDate.class));
                row.put("status", rs.getString("status"));
                return row;
            }
        );
        if (order == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该订单");
        }
        LocalDate serveDate = (LocalDate) order.get("serveDate");
        String status = String.valueOf(order.get("status"));
        if (!MiniappCustomerCancelGuard.canCustomerCancel(LocalDateTime.now(), serveDate, status)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "该订单已过用户可取消时间，仅商家后台可处理");
        }
        Map<String, Object> result = orderPrepService.cancelOrder(orderId);
        publishCustomerEvent("customer.order.changed", customerId, orderId);
        publishCustomerEvent("customer.wallet.changed", customerId, orderId);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> createAfterSale(long customerId, long orderId, MobileCreateAfterSaleRequest request) {
        return aftersaleService.createMobileCase(customerId, orderId, request);
    }

    @Override
    @Transactional
    public Map<String, Object> deleteMiniappOrder(long customerId, long orderId) {
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
        Map<String, Object> result = Map.of("orderId", orderId, "status", "HIDDEN");
        publishCustomerEvent("customer.order.changed", customerId, orderId);
        publishCustomerEvent("customer.wallet.changed", customerId, orderId);
        return result;
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
    public Map<String, Object> setDefaultAddress(String phone, long addressId) {
        return setDefaultAddress(findCustomerIdByPhone(phone), addressId);
    }

    @Transactional
    public Map<String, Object> setDefaultAddress(long customerId, long addressId) {
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
        return Map.of("addressId", addressId, "status", "DEFAULT_UPDATED");
    }

    @Override
    @Transactional
    public Map<String, Object> changeCustomerOrderAddress(long customerId, long orderId, long addressId) {
        Map<String, Object> order = jdbcTemplate.query("""
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
                Map<String, Object> row = new HashMap<>();
                row.put("addressId", rs.getLong("address_id"));
                row.put("serveDate", rs.getObject("serve_date", LocalDate.class));
                return row;
            }
        );
        if (order == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该订单");
        }
        LocalDate serveDate = (LocalDate) order.get("serveDate");
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
        return Map.of("orderId", orderId, "addressId", addressId, "status", "ADDRESS_UPDATED");
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

    private String buildSpecialSummary(String userNote, String adminNote) {
        List<String> parts = new ArrayList<>();
        String normalizedUserNote = normalizeSpecialValue(userNote);
        String normalizedAdminNote = normalizeSpecialValue(adminNote);
        if (!normalizedUserNote.isEmpty()) {
            parts.add("用户备注");
        }
        if (!normalizedAdminNote.isEmpty()) {
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

    public PageResponse<RiderTaskItemResponse> riderTasks(String riderName) {
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

    public RiderBatchSummaryResponse riderSummary(String riderName) {
        LocalDate today = LocalDate.now();
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
        ), riderName, today);
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

    public PageResponse<RiderQueueItemResponse> riderQueue(String riderName) {
        LocalDate today = LocalDate.now();
        List<RiderQueueItemResponse> items = jdbcTemplate.query("""
            SELECT
                dbi.id AS batch_item_id,
                db.id AS batch_id,
                mso.id AS meal_slot_order_id,
                mso.address_id AS address_id,
                dbi.current_sequence,
                c.name AS customer_name,
                c.phone AS customer_phone,
                ca.address_line AS delivery_address,
                mso.meal_period AS production_meal_period,
                COALESCE(mso.delivery_meal_period, mso.meal_period) AS delivery_meal_period,
                COALESCE(ms.meal_name, CASE WHEN mso.meal_period = 'LUNCH' THEN '待配置午餐' ELSE '待配置晚餐' END) AS meal_name,
                mso.quantity,
                COALESCE(mso.user_note, mso.note, '-') AS note,
                COALESCE(mso.merchant_remark, '') AS merchant_remark,
                CASE
                    WHEN TRIM(COALESCE(mso.user_note, mso.note, '')) <> ''
                      OR TRIM(COALESCE(mso.merchant_remark, '')) <> ''
                    THEN TRUE ELSE FALSE
                END AS is_special_order,
                dbi.item_status,
                CASE WHEN dr.id IS NULL THEN 'PENDING' ELSE 'UPLOADED' END AS receipt_status,
                COALESCE(dr.receipt_url, '') AS receipt_url,
                COALESCE(dr.receipt_note, '') AS receipt_note
            FROM dispatch_batch_items dbi
            JOIN dispatch_batches db ON db.id = dbi.batch_id
            JOIN rider_profiles rp ON rp.id = db.rider_profile_id
            JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            JOIN customers c ON c.id = doo.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN menu_week_items ms ON ms.serve_date = doo.serve_date
                AND ms.meal_period = mso.meal_period
                AND ms.slot_status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM menu_weeks mw2 WHERE mw2.id = ms.week_id AND mw2.status = 'PUBLISHED')
            LEFT JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
            WHERE rp.rider_name = ?
              AND db.serve_date = ?
              AND doo.serve_date = ?
            ORDER BY CASE WHEN COALESCE(mso.delivery_meal_period, mso.meal_period) = 'LUNCH' THEN 1 ELSE 2 END, dbi.current_sequence ASC
            """, (rs, rowNum) -> {
            String note = rs.getString("note");
            String merchantRemark = rs.getString("merchant_remark");
            List<String> attentionSources = buildAttentionSources(note, merchantRemark);
            boolean hasAttentionMark = !attentionSources.isEmpty();
            return new RiderQueueItemResponse(
                rs.getLong("batch_item_id"),
                rs.getLong("batch_id"),
                rs.getLong("meal_slot_order_id"),
                rs.getLong("address_id"),
                rs.getInt("current_sequence"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                rs.getString("delivery_address"),
                rs.getString("delivery_meal_period"),
                rs.getString("production_meal_period"),
                rs.getString("delivery_meal_period"),
                rs.getString("meal_name"),
                rs.getInt("quantity"),
                note,
                merchantRemark,
                hasAttentionMark,
                attentionSources,
                buildAttentionLabel(attentionSources),
                hasAttentionMark,
                buildSpecialSummary(note, merchantRemark),
                rs.getString("item_status"),
                rs.getString("receipt_status"),
                rs.getString("receipt_url"),
                rs.getString("receipt_note")
            );
        }, riderName, today, today);
        return PageResponse.of(items, 1, 50, items.size());
    }

    public RiderQueueItemResponse riderQueueItem(long batchItemId, String riderName) {
        LocalDate today = LocalDate.now();
        List<RiderQueueItemResponse> results = jdbcTemplate.query("""
            SELECT
                dbi.id AS batch_item_id,
                db.id AS batch_id,
                mso.id AS meal_slot_order_id,
                mso.address_id AS address_id,
                dbi.current_sequence,
                c.name AS customer_name,
                c.phone AS customer_phone,
                ca.address_line AS delivery_address,
                mso.meal_period AS production_meal_period,
                COALESCE(mso.delivery_meal_period, mso.meal_period) AS delivery_meal_period,
                COALESCE(ms.meal_name, CASE WHEN mso.meal_period = 'LUNCH' THEN '待配置午餐' ELSE '待配置晚餐' END) AS meal_name,
                mso.quantity,
                COALESCE(mso.user_note, mso.note, '-') AS note,
                COALESCE(mso.merchant_remark, '') AS merchant_remark,
                CASE
                    WHEN TRIM(COALESCE(mso.user_note, mso.note, '')) <> ''
                      OR TRIM(COALESCE(mso.merchant_remark, '')) <> ''
                    THEN TRUE ELSE FALSE
                END AS is_special_order,
                dbi.item_status,
                CASE WHEN dr.id IS NULL THEN 'PENDING' ELSE 'UPLOADED' END AS receipt_status,
                COALESCE(dr.receipt_url, '') AS receipt_url,
                COALESCE(dr.receipt_note, '') AS receipt_note
            FROM dispatch_batch_items dbi
            JOIN dispatch_batches db ON db.id = dbi.batch_id
            JOIN rider_profiles rp ON rp.id = db.rider_profile_id
            JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            JOIN customers c ON c.id = doo.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN menu_week_items ms ON ms.serve_date = doo.serve_date
                AND ms.meal_period = mso.meal_period
                AND ms.slot_status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM menu_weeks mw2 WHERE mw2.id = ms.week_id AND mw2.status = 'PUBLISHED')
            LEFT JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
            WHERE dbi.id = ?
              AND rp.rider_name = ?
              AND db.serve_date = ?
              AND doo.serve_date = ?
            """, (rs, rowNum) -> {
            String note = rs.getString("note");
            String merchantRemark = rs.getString("merchant_remark");
            List<String> attentionSources = buildAttentionSources(note, merchantRemark);
            boolean hasAttentionMark = !attentionSources.isEmpty();
            return new RiderQueueItemResponse(
                rs.getLong("batch_item_id"),
                rs.getLong("batch_id"),
                rs.getLong("meal_slot_order_id"),
                rs.getLong("address_id"),
                rs.getInt("current_sequence"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                rs.getString("delivery_address"),
                rs.getString("delivery_meal_period"),
                rs.getString("production_meal_period"),
                rs.getString("delivery_meal_period"),
                rs.getString("meal_name"),
                rs.getInt("quantity"),
                note,
                merchantRemark,
                hasAttentionMark,
                attentionSources,
                buildAttentionLabel(attentionSources),
                hasAttentionMark,
                buildSpecialSummary(note, merchantRemark),
                rs.getString("item_status"),
                rs.getString("receipt_status"),
                rs.getString("receipt_url"),
                rs.getString("receipt_note")
            );
        }, batchItemId, riderName, today, today);
        return results.isEmpty() ? null : results.get(0);
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
    public Map<String, Object> saveBatchAddressReferenceImage(String riderName, RiderBatchAddressReferenceRequest request) {
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
            upsertAddressReferenceImage(addressId, buildReceiptUrl(request.referenceImageUrl()), null, riderName);
            updatedCount++;
        }
        return Map.of(
            "updatedCount", updatedCount,
            "addressIds", new ArrayList<>(uniqueAddressIds)
        );
    }

    @Transactional
    public Map<String, Object> replaceAddressReferenceImage(String riderName, long addressId, String referenceImageUrl) {
        if (isBlank(riderName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "骑手姓名不能为空");
        }
        if (addressId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "地址不能为空");
        }
        if (isBlank(referenceImageUrl)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参考图不能为空");
        }
        String normalizedReferenceImageUrl = buildReceiptUrl(referenceImageUrl);
        upsertAddressReferenceImage(addressId, normalizedReferenceImageUrl, null, riderName);
        return Map.of(
            "addressId", addressId,
            "referenceImageUrl", normalizedReferenceImageUrl,
            "updated", true
        );
    }

    @Override
    public RiderDeliveryUploadResponse uploadRiderReceipt(String riderName, MultipartFile file) {
        if (isBlank(riderName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "骑手姓名不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先选择回执图片");
        }
        if (file.getSize() > MAX_RECEIPT_UPLOAD_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "回执图片不能超过 5MB");
        }

        String extension = resolveUploadExtension(file.getOriginalFilename(), file.getContentType());
        LocalDate today = LocalDate.now();
        String fileName = sanitizeFileKey(riderName) + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;
        Path relativePath = Path.of("rider-receipts", today.toString(), fileName);
        Path targetPath = uploadRootDir.resolve(relativePath).normalize();

        ensureWithinUploadRoot(targetPath);

        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException ex) {
            throw new IllegalStateException("保存回执图片失败", ex);
        }

        String storedPath = toPublicUploadPath(relativePath);
        return new RiderDeliveryUploadResponse(storedPath, storedPath, file.getSize());
    }

    @Transactional
    public Map<String, Object> submitRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM dispatch_assignments
            WHERE meal_slot_order_id = ? AND rider_name = ? AND status = 'DISPATCHING'
            """, Integer.class, mealSlotOrderId, riderName);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RIDER_TASK_NOT_FOUND, "未找到可提交回执的配送任务");
        }
        String finalReceiptUrl = isBlank(receiptFileKey)
            ? ""
            : buildReceiptUrl(receiptFileKey);
        LocalDateTime deliveredDateTime = isBlank(deliveredAt)
            ? LocalDateTime.now().withNano(0)
            : LocalDateTime.parse(deliveredAt);
        LocalDateTime visibleAt = resolveReceiptVisibleAt(mealSlotOrderId, deliveredDateTime);
        LocalDateTime expiresAt = deliveredDateTime.plusHours(48);
        Map<String, Object> result = deliveryService.recordDeliveryReceipt(
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
        List<Long> batchIds = jdbcTemplate.query(
            "SELECT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
            (rs, rowNum) -> rs.getLong("batch_id"),
            mealSlotOrderId
        );
        for (Long batchId : batchIds) {
            refreshRiderBatchState(batchId);
        }
        try {
            Long addressId = findAddressIdByMealSlotOrderId(mealSlotOrderId);
            saveAddressReferenceImageIfAbsent(addressId == null ? 0L : addressId, mealSlotOrderId, finalReceiptUrl, riderName);
        } catch (Exception ex) {
            // Keep receipt submission successful even if reference-image auto-save fails.
        }
        try {
            sendDeliverySubscriptionAfterCutoffIfReached(mealSlotOrderId, deliveredDateTime);
        } catch (Exception ex) {
            // Keep receipt submission successful even if notification delivery fails.
        }
        publishCustomerOrderChanged(mealSlotOrderId);
        publishRiderEvent("dispatch.receipt.changed", riderName, mealSlotOrderId);
        return result;
    }

    @Transactional
    public Map<String, Object> updateRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt) {
        // 验证骑手是否有权限更新此订单的回执
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM dispatch_assignments
            WHERE meal_slot_order_id = ? AND rider_name = ?
            """, Integer.class, mealSlotOrderId, riderName);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RIDER_TASK_NOT_FOUND, "未找到该配送任务");
        }

        // 检查是否已有回执记录
        Integer receiptCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM delivery_receipts
            WHERE meal_slot_order_id = ?
            """, Integer.class, mealSlotOrderId);
        if (receiptCount == null || receiptCount == 0) {
            throw new BusinessException(ErrorCode.RIDER_TASK_NOT_FOUND, "未找到回执记录");
        }
        String previousReceiptUrl = jdbcTemplate.queryForObject("""
            SELECT receipt_url
            FROM delivery_receipts
            WHERE meal_slot_order_id = ?
            """, String.class, mealSlotOrderId);

        String finalReceiptUrl = isBlank(receiptFileKey)
            ? ""
            : buildReceiptUrl(receiptFileKey);
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
        deleteManagedReceiptFileQuietly(previousReceiptUrl, finalReceiptUrl);
        publishCustomerOrderChanged(mealSlotOrderId);
        publishRiderEvent("dispatch.receipt.changed", riderName, mealSlotOrderId);

        return Map.of(
            "mealSlotOrderId", mealSlotOrderId,
            "orderStatus", "DELIVERED",
            "receiptUrl", finalReceiptUrl,
            "visibleAt", visibleAt.toString(),
            "expiresAt", expiresAt.toString(),
            "updated", true
        );
    }

    @Transactional
    public Map<String, Object> deleteRiderReceiptImage(long mealSlotOrderId, String riderName) {
        // 验证骑手是否有权限删除此订单的回执照片
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM dispatch_assignments
            WHERE meal_slot_order_id = ? AND rider_name = ?
            """, Integer.class, mealSlotOrderId, riderName);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RIDER_TASK_NOT_FOUND, "未找到该配送任务");
        }

        // 检查是否已有回执记录
        Integer receiptCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM delivery_receipts
            WHERE meal_slot_order_id = ?
            """, Integer.class, mealSlotOrderId);
        if (receiptCount == null || receiptCount == 0) {
            throw new BusinessException(ErrorCode.RIDER_TASK_NOT_FOUND, "未找到回执记录");
        }
        String previousReceiptUrl = jdbcTemplate.queryForObject("""
            SELECT receipt_url
            FROM delivery_receipts
            WHERE meal_slot_order_id = ?
            """, String.class, mealSlotOrderId);

        // 清空照片URL，但保留回执记录
        jdbcTemplate.update("""
            UPDATE delivery_receipts
            SET receipt_url = '',
                visible_to_customer = FALSE
            WHERE meal_slot_order_id = ?
            """, mealSlotOrderId);
        deleteManagedReceiptFileQuietly(previousReceiptUrl, "");
        publishCustomerOrderChanged(mealSlotOrderId);
        publishRiderEvent("dispatch.receipt.changed", riderName, mealSlotOrderId);

        return Map.of(
            "mealSlotOrderId", mealSlotOrderId,
            "orderStatus", "DELIVERED",
            "receiptUrl", "",
            "deleted", true
        );
    }

    @Transactional
    public Map<String, Object> reorderRiderQueue(String riderName, List<Long> batchItemIds) {
        if (batchItemIds == null || batchItemIds.isEmpty()) {
            return Map.of("updatedCount", 0);
        }

        List<Map<String, Object>> currentRows = jdbcTemplate.query(
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
            rs -> {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("batchId", rs.getLong("batch_id"));
                    row.put("sequence", rs.getInt("current_sequence"));
                    rows.add(row);
                }
                return rows;
            }
        );
        if (currentRows.isEmpty()) {
            return Map.of("updatedCount", 0);
        }

        Long batchId = ((Number) currentRows.get(0).get("batchId")).longValue();
        for (Map<String, Object> row : currentRows) {
            long currentBatchId = ((Number) row.get("batchId")).longValue();
            if (currentBatchId != batchId.longValue()) {
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

        jdbcTemplate.update(
            "UPDATE dispatch_batch_items SET current_sequence = current_sequence + 1000 WHERE batch_id = ?",
            batchId
        );
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
        return Map.of("updatedCount", batchItemIds.size(), "status", "REORDERED");
    }

    @Transactional
    public Map<String, Object> deferRiderQueueItem(String riderName, long batchItemId) {
        RiderBatchItemContext context = requireRiderBatchItem(riderName, batchItemId);
        if ("DELIVERED".equals(context.itemStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "已送达订单不能稍后送");
        }
        if ("DEFERRED".equals(context.itemStatus())) {
            return Map.of(
                "batchItemId", batchItemId,
                "itemStatus", "DEFERRED",
                "status", "UNCHANGED"
            );
        }
        int lastSequence = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(current_sequence), 0) FROM dispatch_batch_items WHERE batch_id = ?",
            Integer.class,
            context.batchId()
        );
        // 先移出范围避免唯一约束冲突
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
        return Map.of(
            "batchItemId", batchItemId,
            "itemStatus", "DEFERRED",
            "status", "DEFERRED"
        );
    }

    @Transactional
    public Map<String, Object> resumeRiderQueueItem(String riderName, long batchItemId) {
        RiderBatchItemContext context = requireRiderBatchItem(riderName, batchItemId);
        if ("DELIVERED".equals(context.itemStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "已送达订单不能恢复队列");
        }
        if (!"DEFERRED".equals(context.itemStatus())) {
            return Map.of(
                "batchItemId", batchItemId,
                "itemStatus", context.itemStatus(),
                "status", "UNCHANGED"
            );
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

        return Map.of(
            "batchItemId", batchItemId,
            "itemStatus", finalStatus != null ? finalStatus : "PENDING",
            "status", "RESUMED"
        );
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
        Integer deliveredCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ? AND item_status = 'DELIVERED'",
            Integer.class,
            batchId
        );
        Integer totalCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ?",
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
        jdbcTemplate.query(
            """
                SELECT meal_slot_order_id, current_sequence, item_status
                FROM dispatch_batch_items
                WHERE batch_id = ?
                ORDER BY current_sequence ASC, id ASC
                """,
            ps -> ps.setLong(1, batchId),
            rs -> {
                while (rs.next()) {
                    long orderId = rs.getLong("meal_slot_order_id");
                    int sequenceNumber = rs.getInt("current_sequence");
                    String assignmentStatus = mapAssignmentStatus(rs.getString("item_status"));
                    jdbcTemplate.update(
                        """
                            UPDATE dispatch_assignments
                            SET sequence_number = ?,
                                status = ?
                            WHERE meal_slot_order_id = ?
                        """,
                        sequenceNumber,
                        assignmentStatus,
                        orderId
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
            // #region debug-point D:snapshot-attempt
            reportMiniappOrderDebug("pre-fix", "D", String.valueOf(mealSlotOrderId), "attemptWriteOrderSnapshot:attempt", Map.of(
                "mealSlotOrderId", mealSlotOrderId,
                "customerId", customerId
            ));
            // #endregion
            orderNoteSnapshotService.writeOrderSnapshot(
                mealSlotOrderId,
                customerId,
                operatorName,
                orderUserNote,
                subscriptionDefaultNote,
                orderOnceMerchantNotes,
                snapshotTime
            );
            // #region debug-point D:snapshot-success
            reportMiniappOrderDebug("pre-fix", "D", String.valueOf(mealSlotOrderId), "attemptWriteOrderSnapshot:success", Map.of(
                "mealSlotOrderId", mealSlotOrderId,
                "customerId", customerId
            ));
            // #endregion
        } catch (RuntimeException ex) {
            // #region debug-point D:snapshot-error
            reportMiniappOrderDebug("pre-fix", "D", String.valueOf(mealSlotOrderId), "attemptWriteOrderSnapshot:error", Map.of(
                "mealSlotOrderId", mealSlotOrderId,
                "customerId", customerId,
                "errorType", ex.getClass().getName(),
                "errorMessage", safeString(ex.getMessage())
            ));
            // #endregion
            log.warn(
                "miniapp create order snapshot skipped customerId={} orderId={} reason={}",
                customerId,
                mealSlotOrderId,
                ex.getMessage(),
                ex
            );
        }
    }

    private void sendDeliverySubscriptionAfterCutoffIfReached(long mealSlotOrderId, LocalDateTime deliveredDateTime) {
        DeliveryMealSlotContext context = findDeliveryMealSlotContext(mealSlotOrderId);
        if (context == null || !hasReachedDeliveryNotifyCutoff(context.mealPeriod(), context.serveDate(), deliveredDateTime)) {
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

    private boolean isAcceptedSubscribeResult(String acceptResult) {
        String normalized = safeString(acceptResult).trim();
        return "accept".equalsIgnoreCase(normalized)
            || "acceptWithAudio".equalsIgnoreCase(normalized)
            || "acceptWithAlert".equalsIgnoreCase(normalized);
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

    private record RiderBatchItemContext(
        long batchItemId,
        long batchId,
        int currentSequence,
        String itemStatus
    ) {
    }

    private record ContactSnapshot(
        String name,
        String phone
    ) {
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
        Map<String, Object> customer = jdbcTemplate.queryForMap(
            "SELECT name, phone FROM customers WHERE id = ? AND active = TRUE",
            customerId
        );
        String finalName = String.valueOf(customer.get("name")).trim();
        String finalPhone = String.valueOf(customer.get("phone")).replaceAll("\\D", "");
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
        if (!LocalTime.now().isBefore(LocalTime.of(23, 0))) {
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
        Map<LocalDate, List<Map<String, Object>>> grouped = new HashMap<>();
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
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("meal_period", rs.getString("meal_period"));
                    row.put("slot_status", rs.getString("slot_status"));
                    row.put("dish_items_json", rs.getString("dish_items_json"));
                    row.put("total_calories", rs.getObject("total_calories"));
                    row.put("merchant_note", rs.getString("merchant_note"));
                    grouped.computeIfAbsent(serveDate, key -> new ArrayList<>()).add(row);
                }
                return null;
            }
        );
        for (int i = 0; i < 7; i++) {
            LocalDate serveDate = monday.plusDays(i);
            List<Map<String, Object>> rows = grouped.getOrDefault(serveDate, List.of());
            String slotStatus = summarizeDayStatus(rows, serveDate);
            List<MobileMenuItemResponse> items = rows.stream()
                .filter(row -> "ACTIVE".equals(row.get("slot_status")))
                .map(row -> new MobileMenuItemResponse(
                    ((Number) row.get("id")).longValue(),
                    serveDate.toString(),
                    String.valueOf(row.get("meal_period")),
                    readDishItems(String.valueOf(row.get("dish_items_json"))),
                    row.get("total_calories") == null ? null : ((Number) row.get("total_calories")).intValue(),
                    String.valueOf(row.get("merchant_note")),
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

    private String summarizeDayStatus(List<Map<String, Object>> rows, LocalDate serveDate) {
        if (rows.isEmpty()) {
            return serveDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? "REST" : "UNCONFIGURED";
        }
        boolean hasActive = rows.stream().anyMatch(row -> "ACTIVE".equals(row.get("slot_status")));
        if (hasActive) {
            return "ACTIVE";
        }
        boolean allRest = rows.stream().allMatch(row -> "REST".equals(row.get("slot_status")));
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
        Map<String, Object> customer = jdbcTemplate.query(
            "SELECT name, phone FROM customers WHERE id = ?",
            ps -> ps.setLong(1, customerId),
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("name", rs.getString("name"));
                row.put("phone", rs.getString("phone"));
                return row;
            }
        );
        if (customer == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应客户");
        }
            long insertedAddressId = insertAndReturnId(
                "INSERT INTO customer_addresses (customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (?, ?, ?, ?, ?, FALSE)",
                customerId,
                String.valueOf(customer.get("name")),
                String.valueOf(customer.get("phone")),
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
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && !keys.isEmpty()) {
            Object idValue = keys.containsKey("ID") ? keys.get("ID") : keys.get("id");
            if (idValue == null) {
                idValue = keys.values().iterator().next();
            }
            if (idValue instanceof Number number) {
                return number.longValue();
            }
        }
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        return 0L;
    }

    private long resolveLatestDailyOrderId(long customerId, LocalDate serveDate) {
        Long resolvedId = jdbcTemplate.query(
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
        if (resolvedId == null || resolvedId <= 0) {
            throw new IllegalStateException("无法定位刚创建的日订单");
        }
        return resolvedId;
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
                // #region debug-point C:merchant-remark-empty
                reportMiniappOrderDebug("pre-fix", "C", String.valueOf(customerId), "normalizeCustomerMerchantRemark:empty", Map.of(
                    "customerId", customerId
                ));
                // #endregion
                return "";
            }
            String normalized = remarks.get(0) == null ? "" : remarks.get(0).trim();
            // #region debug-point C:merchant-remark-success
            reportMiniappOrderDebug("pre-fix", "C", String.valueOf(customerId), "normalizeCustomerMerchantRemark:success", Map.of(
                "customerId", customerId,
                "hasMerchantRemark", !normalized.isEmpty()
            ));
            // #endregion
            return "-".equals(normalized) ? "" : normalized;
        } catch (RuntimeException ex) {
            // #region debug-point C:merchant-remark-error
            reportMiniappOrderDebug("pre-fix", "C", String.valueOf(customerId), "normalizeCustomerMerchantRemark:error", Map.of(
                "customerId", customerId,
                "errorType", ex.getClass().getName(),
                "errorMessage", safeString(ex.getMessage())
            ));
            // #endregion
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

    private void reportMiniappOrderDebug(String runId, String hypothesisId, String traceId, String msg, Map<String, Object> data) {
        try {
            Map<String, String> debugEnv = readMiniappOrderDebugEnv();
            String debugServerUrl = debugEnv.getOrDefault("DEBUG_SERVER_URL", "");
            String debugSessionId = debugEnv.getOrDefault("DEBUG_SESSION_ID", "miniapp-order-500");
            if (isBlank(debugServerUrl)) {
                return;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", debugSessionId);
            payload.put("runId", runId);
            payload.put("hypothesisId", hypothesisId);
            payload.put("location", "MobilePortalServiceImpl");
            payload.put("msg", "[DEBUG] " + msg);
            payload.put("data", data == null ? Map.of() : data);
            payload.put("traceId", traceId);
            payload.put("ts", System.currentTimeMillis());
            HttpRequest request = HttpRequest.newBuilder(URI.create(debugServerUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
            HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // ignore debug reporting failures
        }
    }

    private Map<String, String> readMiniappOrderDebugEnv() {
        Map<String, String> result = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(Path.of(DEBUG_MINIAPP_ORDER_ENV_FILE));
            for (String line : lines) {
                int index = line.indexOf('=');
                if (index <= 0) {
                    continue;
                }
                result.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
            }
        } catch (IOException ignored) {
            // ignore debug env read failures
        }
        return result;
    }

    private String sanitizeFileKey(String riderName) {
        String base = isBlank(riderName) ? "rider" : riderName.trim();
        return base.replaceAll("[^0-9A-Za-z\\u4e00-\\u9fa5_-]", "_");
    }

    private String resolveUploadExtension(String originalFilename, String contentType) {
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase();
        if (lowerContentType.contains("png")) {
            return ".png";
        }
        if (lowerContentType.contains("webp")) {
            return ".webp";
        }
        if (lowerContentType.contains("gif")) {
            return ".gif";
        }

        String normalizedName = originalFilename == null ? "" : originalFilename.trim().toLowerCase();
        if (normalizedName.endsWith(".png")) {
            return ".png";
        }
        if (normalizedName.endsWith(".webp")) {
            return ".webp";
        }
        if (normalizedName.endsWith(".gif")) {
            return ".gif";
        }
        return ".jpg";
    }

    private void ensureWithinUploadRoot(Path targetPath) {
        if (!targetPath.startsWith(uploadRootDir)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的图片存储路径");
        }
    }

    private String toPublicUploadPath(Path relativePath) {
        return "/uploads/" + relativePath.toString().replace('\\', '/');
    }

    private String buildReceiptUrl(String receiptFileKey) {
        String normalized = isBlank(receiptFileKey) ? "" : receiptFileKey.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.startsWith("cloud://")) {
            return normalized;
        }
        if (normalized.startsWith("/uploads/")) {
            return normalized;
        }
        if (normalized.startsWith("uploads/")) {
            return "/" + normalized;
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            try {
                URI uri = URI.create(normalized);
                String path = uri.getPath();
                if (path != null && path.startsWith("/uploads/")) {
                    return path;
                }
            } catch (IllegalArgumentException ignored) {
                return normalized;
            }
        }
        return normalized;
    }

    private void deleteManagedReceiptFileQuietly(String previousReceiptUrl, String nextReceiptUrl) {
        String previousPath = buildReceiptUrl(previousReceiptUrl);
        String nextPath = buildReceiptUrl(nextReceiptUrl);
        if (isBlank(previousPath) || previousPath.equals(nextPath) || !previousPath.startsWith("/uploads/")) {
            return;
        }

        Path relativePath = Path.of(previousPath.substring("/uploads/".length()));
        Path filePath = uploadRootDir.resolve(relativePath).normalize();
        ensureWithinUploadRoot(filePath);

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Ignore stale file cleanup failures so receipt flow itself can continue.
        }
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

    private LocalDateTime resolveReceiptVisibleAt(long mealSlotOrderId, LocalDateTime deliveredDateTime) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
            SELECT do.serve_date, mso.meal_period
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE mso.id = ?
            """, mealSlotOrderId);
        LocalDate serveDate = ((java.sql.Date) row.get("serve_date")).toLocalDate();
        String mealPeriod = String.valueOf(row.get("meal_period"));
        LocalDateTime threshold = resolveDeliveryNotifyThreshold(serveDate, mealPeriod);
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
    public Map<String, Object> reportDeliveryException(
        long mealSlotOrderId,
        String riderName,
        String exceptionType,
        String exceptionNote,
        List<String> exceptionImages
    ) {
        // 1. 查询骑手ID和订单信息
        Map<String, Object> orderInfo = jdbcTemplate.query("""
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
                Map<String, Object> row = new HashMap<>();
                row.put("riderProfileId", rs.getLong("rider_profile_id"));
                row.put("customerPhone", rs.getString("customer_phone"));
                row.put("deliveryAddress", rs.getString("delivery_address"));
                return row;
            }
        );

        if (orderInfo == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应订单或骑手信息");
        }

        // 2. 保存异常记录
        String imagesJson = null;
        if (exceptionImages != null && !exceptionImages.isEmpty()) {
            try {
                imagesJson = objectMapper.writeValueAsString(exceptionImages);
            } catch (JsonProcessingException e) {
                // 忽略JSON序列化错误
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
            ((Number) orderInfo.get("riderProfileId")).longValue(),
            riderName,
            exceptionType,
            exceptionNote,
            orderInfo.get("customerPhone"),
            orderInfo.get("deliveryAddress"),
            imagesJson
        );
        publishRiderEvent("dispatch.exception.changed", riderName, mealSlotOrderId);

        return Map.of(
            "exceptionId", exceptionId,
            "status", "REPORTED",
            "message", "异常已上报，请等待处理"
        );
    }

    @Override
    public PageResponse<RiderTaskItemResponse> riderCompletedToday(String riderName) {
        LocalDate today = LocalDate.now();
        List<RiderTaskItemResponse> items = jdbcTemplate.query("""
            SELECT
                da.id AS dispatch_id,
                mso.id AS meal_slot_order_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                ca.address_line AS delivery_address,
                mso.meal_period,
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

    @Override
    public Map<String, Object> revertOrderStatus(long mealSlotOrderId, String riderName) {
        Map<String, Object> result = extension.revertOrderStatus(mealSlotOrderId, riderName);
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
    public Map<String, Object> saveOrderSequence(String riderName, String mealPeriod, List<Long> batchItemIds) {
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
