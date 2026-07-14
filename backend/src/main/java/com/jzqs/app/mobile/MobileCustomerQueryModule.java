package com.jzqs.app.mobile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.customer.api.WalletTransactionResponse;
import com.jzqs.app.mobile.api.MobileBannerItemResponse;
import com.jzqs.app.mobile.api.MobileCurrentWeekDayResponse;
import com.jzqs.app.mobile.api.MobileCurrentWeekResponse;
import com.jzqs.app.mobile.api.MobileHomeResponse;
import com.jzqs.app.mobile.api.MobileMenuItemResponse;
import com.jzqs.app.mobile.api.MobileOrderItemResponse;
import com.jzqs.app.mobile.api.MobileTomorrowMenuResponse;
import com.jzqs.app.mobile.api.MobileWeekMenuDayResponse;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class MobileCustomerQueryModule {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DeliverySubscriptionModule deliverySubscriptionModule;
    private final LocalTime selfOrderCutoffTime;

    MobileCustomerQueryModule(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        DeliverySubscriptionModule deliverySubscriptionModule,
        @org.springframework.beans.factory.annotation.Value("${app.mobile.self-order-cutoff:23:00}") String selfOrderCutoff
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.deliverySubscriptionModule = deliverySubscriptionModule;
        this.selfOrderCutoffTime = LocalTime.parse(selfOrderCutoff);
    }

    MobileHomeResponse guestHome() {
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

    MobileHomeResponse customerHome(long customerId) {
        CustomerHomeSnapshot customer = jdbcTemplate.query(
            """
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
                """,
            ps -> ps.setLong(1, customerId),
            rs -> {
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
            }
        );
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

    PageResponse<MobileMenuItemResponse> publishedMenus(String serveDate) {
        List<MobileMenuItemResponse> items = jdbcTemplate.query(
            """
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
                """,
            (rs, rowNum) -> new MobileMenuItemResponse(
                rs.getLong("id"),
                rs.getDate("serve_date").toLocalDate().toString(),
                rs.getString("meal_period"),
                readDishItems(rs.getString("dish_items_json")),
                (Integer) rs.getObject("total_calories"),
                rs.getString("merchant_note"),
                rs.getString("status")
            ),
            LocalDate.parse(serveDate)
        );
        return PageResponse.of(items, 1, 20, items.size());
    }

    MobileCurrentWeekResponse currentWeekMenu() {
        LocalDate monday = currentWeekMonday(LocalDate.now());
        LocalDate sunday = monday.plusDays(6);
        return new MobileCurrentWeekResponse(
            monday.toString(),
            sunday.toString(),
            buildCurrentWeekDays(monday)
        );
    }

    MobileTomorrowMenuResponse tomorrowMenu() {
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
        boolean orderingEnabled = settings.orderingEnabled();
        String notice = settings.holidayNoticeDesc();

        boolean selfOrderEnabled = LocalTime.now().isBefore(selfOrderCutoffTime);
        String selfOrderNotice = selfOrderEnabled ? "" : "当前自助下单已截止，如需加单请联系专属客服微信";

        boolean canOrder = true;
        String statusText = "";
        if (!orderingEnabled) {
            canOrder = false;
            statusText = !isBlank(notice) ? notice : "当前自助下单已截止，请联系专属客服微信";
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

    List<MobileWeekMenuDayResponse> weekMenus(String startDate) {
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

    PageResponse<MobileOrderItemResponse> customerOrders(long customerId, String status) {
        List<MobileOrderItemResponse> items = jdbcTemplate.query(
            """
                SELECT
                    mso.id,
                    do.serve_date AS serve_date,
                    mso.meal_period,
                    COALESCE(ms.meal_name, CASE WHEN mso.meal_period = 'LUNCH' THEN '待配送午餐' ELSE '待配送晚餐' END) AS meal_name,
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
                """,
            (rs, rowNum) -> new MobileOrderItemResponse(
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
            ),
            customerId,
            status,
            status,
            status
        );
        return PageResponse.of(items, 1, 20, items.size());
    }

    PageResponse<WalletTransactionResponse> walletTransactions(long customerId) {
        List<WalletTransactionResponse> items = jdbcTemplate.query(
            """
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
                """,
            (rs, rowNum) -> new WalletTransactionResponse(
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
            ),
            customerId
        );
        return PageResponse.of(items, 1, 20, items.size());
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
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
        LocalDateTime gate = deliverySubscriptionModule.resolveDeliveryNotifyThreshold(serveDate, mealPeriod);
        return LocalDateTime.now().isBefore(gate) ? "PENDING_DISPATCH" : "DELIVERED";
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

    private List<MobileCurrentWeekDayResponse> buildCurrentWeekDays(LocalDate monday) {
        Long weekId = findPublishedWeekId(monday);
        List<MobileCurrentWeekDayResponse> days = new ArrayList<>();
        if (weekId == null) {
            for (int i = 0; i < 7; i++) {
                LocalDate serveDate = monday.plusDays(i);
                String slotStatus = serveDate.getDayOfWeek() == DayOfWeek.SUNDAY ? "REST" : "UNCONFIGURED";
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
            return serveDate.getDayOfWeek() == DayOfWeek.SUNDAY ? "REST" : "UNCONFIGURED";
        }
        boolean hasActive = rows.stream().anyMatch(row -> "ACTIVE".equals(row.slotStatus()));
        if (hasActive) {
            return "ACTIVE";
        }
        boolean allRest = rows.stream().allMatch(row -> "REST".equals(row.slotStatus()));
        return allRest ? "REST" : "UNCONFIGURED";
    }

    private int remainingValidityDays(LocalDateTime expiredAt) {
        if (expiredAt == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), expiredAt.toLocalDate());
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

    private boolean canChangeAddress(LocalDate serveDate) {
        return serveDate != null && serveDate.isAfter(LocalDate.now());
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
        } catch (JsonProcessingException ex) {
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
            } catch (NumberFormatException ex) {
                seconds = 3;
            }
        }
        return Math.max(1, seconds) * 1000;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
}
