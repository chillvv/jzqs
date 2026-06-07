package com.jzqs.app.mobile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.customer.api.WalletTransactionResponse;
import com.jzqs.app.delivery.service.DeliveryService;
import com.jzqs.app.mobile.api.MobileAddressResponse;
import com.jzqs.app.mobile.api.MobileAfterSaleItemResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import com.jzqs.app.mobile.api.MobileCurrentWeekDayResponse;
import com.jzqs.app.mobile.api.MobileCurrentWeekResponse;
import com.jzqs.app.mobile.api.MobileHomeResponse;
import com.jzqs.app.mobile.api.MobileMenuItemResponse;
import com.jzqs.app.mobile.api.MobileOrderItemResponse;
import com.jzqs.app.mobile.api.MobileTomorrowMenuResponse;
import com.jzqs.app.mobile.api.MobileWeekMenuDayResponse;
import com.jzqs.app.mobile.api.RiderBatchSummaryResponse;
import com.jzqs.app.mobile.api.RiderDeliveryUploadResponse;
import com.jzqs.app.mobile.api.RiderQueueItemResponse;
import com.jzqs.app.mobile.api.RiderTaskItemResponse;
import java.io.IOException;
import java.net.URI;
import com.jzqs.app.order.service.OrderPrepService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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
    private static final long MAX_RECEIPT_UPLOAD_SIZE = 5L * 1024 * 1024;
    private final JdbcTemplate jdbcTemplate;
    private final OrderPrepService orderPrepService;
    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;
    private final MobilePortalServiceExtension extension;
    private final AftersaleService aftersaleService;
    private final Path uploadRootDir;

    public MobilePortalServiceImpl(
        JdbcTemplate jdbcTemplate,
        OrderPrepService orderPrepService,
        DeliveryService deliveryService,
        ObjectMapper objectMapper,
        MobilePortalServiceExtension extension,
        AftersaleService aftersaleService,
        @Value("${app.upload-dir:./uploads}") String uploadDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderPrepService = orderPrepService;
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
        this.extension = extension;
        this.aftersaleService = aftersaleService;
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
            "SELECT ordering_enabled, holiday_notice_title, holiday_notice_desc, banner_images, popup_announcement_enabled, popup_announcement_content FROM admin_settings WHERE id = 1"
        );
        boolean orderingEnabled = Boolean.TRUE.equals(settings.get("ordering_enabled"));
        return new MobileHomeResponse(
            0L,
            "微信用户",
            "",
            "未开通套餐",
            0,
            0,
            orderingEnabled,
            orderingEnabled ? "可浏览菜单" : "暂停接单",
            safeString(settings.get("holiday_notice_title")),
            safeString(settings.get("holiday_notice_desc")),
            "",
            "",
            parseBannerImages(settings.get("banner_images")),
            Boolean.TRUE.equals(settings.get("popup_announcement_enabled")),
            safeString(settings.get("popup_announcement_content"))
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
                c.remark,
                (
                    SELECT ca.address_line
                    FROM customer_addresses ca
                    WHERE ca.customer_id = c.id
                    ORDER BY ca.is_default DESC, ca.id ASC
                    LIMIT 1
                ) AS default_address
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
            row.put("remark", rs.getString("remark"));
            row.put("default_address", rs.getString("default_address"));
            return row;
        });
        if (customer == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应客户");
        }
        Map<String, Object> settings = jdbcTemplate.queryForMap(
            "SELECT ordering_enabled, holiday_notice_title, holiday_notice_desc, banner_images, popup_announcement_enabled, popup_announcement_content FROM admin_settings WHERE id = 1"
        );
        boolean orderingEnabled = Boolean.TRUE.equals(settings.get("ordering_enabled"));
        return new MobileHomeResponse(
            ((Number) customer.get("id")).longValue(),
            String.valueOf(customer.get("name")),
            String.valueOf(customer.get("phone")),
            String.valueOf(customer.get("package_name")),
            ((Number) customer.get("total_meals")).intValue(),
            ((Number) customer.get("remaining_meals")).intValue(),
            orderingEnabled,
            orderingEnabled ? "可下单" : "暂停接单",
            safeString(settings.get("holiday_notice_title")),
            safeString(settings.get("holiday_notice_desc")),
            customer.get("default_address") == null ? "" : String.valueOf(customer.get("default_address")),
            customer.get("remark") == null ? "" : String.valueOf(customer.get("remark")),
            parseBannerImages(settings.get("banner_images")),
            Boolean.TRUE.equals(settings.get("popup_announcement_enabled")),
            safeString(settings.get("popup_announcement_content"))
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
            rs.getString("receipt_url"),
            rs.getString("receipt_note"),
            formatTimestamp(rs.getTimestamp("delivered_at")),
                rs.getBoolean("receipt_visible"),
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
        ensureOrderingEnabled();
        ensureSelfOrderAllowed(serveDate, mealPeriod);
        requirePublishedMenu(serveDate, mealPeriod);
        
        LocalDate orderDate = LocalDate.parse(serveDate);
        
        long walletId = activeWalletId(customerId);
        int remainingMeals = remainingMealsForUpdate(walletId);
        if (remainingMeals <= 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_MEALS, "剩余餐次不足，无法下单");
        }
        long addressId = ensureCustomerAddress(customerId, deliveryAddress);
        ensureNoDuplicateMealOrder(customerId, orderDate, mealPeriod);
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
        long dailyOrderId = existingDailyOrderId == null
            ? insertAndReturnId(
                "INSERT INTO daily_orders (customer_id, serve_date, source, status, locked, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                customerId, orderDate, "MINIAPP", "PENDING_DISPATCH", false, Timestamp.valueOf(now)
            )
            : existingDailyOrderId;
        String finalUserNote = normalizeNote(note);
        long mealSlotOrderId = insertAndReturnId(
            """
                INSERT INTO meal_slot_orders (
                    daily_order_id, meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            dailyOrderId, mealPeriod, 1, addressId, finalUserNote, finalUserNote, "PENDING_DISPATCH", "MINIAPP"
        );
        jdbcTemplate.update(
            "UPDATE daily_orders SET status = 'PENDING_DISPATCH', source = 'MINIAPP' WHERE id = ?",
            dailyOrderId
        );
        jdbcTemplate.update(
            "UPDATE customers SET last_order_at = ?, remark = ? WHERE id = ?",
            Timestamp.valueOf(now),
            normalizeCustomerRemark(note),
            customerId
        );
        jdbcTemplate.update("UPDATE meal_wallets SET reserved_meals = reserved_meals + 1 WHERE id = ?", walletId);
        insertWalletTransaction(walletId, "RESERVE", -1, "小程序", "用户自主下单占用餐次", now, mealSlotOrderId);
        return Map.of(
            "orderId", mealSlotOrderId,
            "status", "PENDING_DISPATCH",
            "walletAction", "RESERVED"
        );
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
        return orderPrepService.cancelOrder(orderId);
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
        return Map.of("orderId", orderId, "status", "HIDDEN");
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
        if (isDefault) {
            jdbcTemplate.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?", customerId);
        }
        long addressId = insertAndReturnId(
            """
                INSERT INTO customer_addresses (customer_id, contact_name, contact_phone, address_line, area_code, is_default)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            customerId,
            contactName.trim(),
            contactPhone.trim(),
            addressLine.trim(),
            areaCode.trim(),
            isDefault
        );
        return new MobileAddressResponse(addressId, contactName.trim(), contactPhone.trim(), addressLine.trim(), areaCode.trim(), isDefault);
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

    public PageResponse<RiderTaskItemResponse> riderTasks(String riderName) {
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
              AND do.serve_date = CURRENT_DATE
            ORDER BY CASE WHEN da.status = 'DISPATCHING' THEN 0 ELSE 1 END, da.id DESC
            """, (rs, rowNum) -> new RiderTaskItemResponse(
            rs.getLong("dispatch_id"),
            rs.getLong("meal_slot_order_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("delivery_address"),
            rs.getString("meal_period"),
            rs.getString("meal_name"),
            rs.getString("note"),
            rs.getString("delivery_status"),
            rs.getString("receipt_status"),
            rs.getString("receipt_url")
        ), riderName);
        return PageResponse.of(items, 1, 20, items.size());
    }

    public RiderBatchSummaryResponse riderSummary(String riderName) {
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
              AND db.serve_date = CURRENT_DATE
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
        ), riderName);
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
        List<RiderQueueItemResponse> items = jdbcTemplate.query("""
            SELECT
                dbi.id AS batch_item_id,
                db.id AS batch_id,
                mso.id AS meal_slot_order_id,
                dbi.current_sequence,
                c.name AS customer_name,
                c.phone AS customer_phone,
                ca.address_line AS delivery_address,
                mso.meal_period,
                COALESCE(ms.meal_name, CASE WHEN mso.meal_period = 'LUNCH' THEN '待配置午餐' ELSE '待配置晚餐' END) AS meal_name,
                mso.quantity,
                COALESCE(mso.user_note, mso.note, '-') AS note,
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
              AND db.serve_date = CURRENT_DATE
            ORDER BY CASE WHEN db.meal_period = 'LUNCH' THEN 1 ELSE 2 END, dbi.current_sequence ASC
            """, (rs, rowNum) -> new RiderQueueItemResponse(
            rs.getLong("batch_item_id"),
            rs.getLong("batch_id"),
            rs.getLong("meal_slot_order_id"),
            rs.getInt("current_sequence"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("delivery_address"),
            rs.getString("meal_period"),
            rs.getString("meal_name"),
            rs.getInt("quantity"),
            rs.getString("note"),
            rs.getString("item_status"),
            rs.getString("receipt_status"),
            rs.getString("receipt_url"),
            rs.getString("receipt_note")
        ), riderName);
        return PageResponse.of(items, 1, 50, items.size());
    }

    public RiderQueueItemResponse riderQueueItem(long batchItemId, String riderName) {
        List<RiderQueueItemResponse> results = jdbcTemplate.query("""
            SELECT
                dbi.id AS batch_item_id,
                db.id AS batch_id,
                mso.id AS meal_slot_order_id,
                dbi.current_sequence,
                c.name AS customer_name,
                c.phone AS customer_phone,
                ca.address_line AS delivery_address,
                mso.meal_period,
                COALESCE(ms.meal_name, CASE WHEN mso.meal_period = 'LUNCH' THEN '待配置午餐' ELSE '待配置晚餐' END) AS meal_name,
                mso.quantity,
                COALESCE(mso.user_note, mso.note, '-') AS note,
                dbi.item_status,
                CASE WHEN dr.id IS NULL THEN 'PENDING' ELSE 'UPLOADED' END AS receipt_status,
                COALESCE(dr.receipt_url, '') AS receipt_url,
                COALESCE(dr.receipt_note, '') AS receipt_note
            FROM dispatch_batch_items dbi
            JOIN dispatch_batches db ON db.id = dbi.batch_id
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
            """, (rs, rowNum) -> new RiderQueueItemResponse(
            rs.getLong("batch_item_id"),
            rs.getLong("batch_id"),
            rs.getLong("meal_slot_order_id"),
            rs.getInt("current_sequence"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("delivery_address"),
            rs.getString("meal_period"),
            rs.getString("meal_name"),
            rs.getInt("quantity"),
            rs.getString("note"),
            rs.getString("item_status"),
            rs.getString("receipt_status"),
            rs.getString("receipt_url"),
            rs.getString("receipt_note")
        ), batchItemId);
        return results.isEmpty() ? null : results.get(0);
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
        int sequence = 1;
        for (Long batchItemId : batchItemIds) {
            jdbcTemplate.update("""
                UPDATE dispatch_batch_items
                SET current_sequence = ?, manually_adjusted = TRUE, reordered_by = ?, reordered_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, sequence++, riderName, batchItemId);
        }
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
        jdbcTemplate.update("""
            UPDATE dispatch_batch_items
            SET current_sequence = current_sequence - 1,
                manually_adjusted = TRUE,
                reordered_by = ?,
                reordered_at = CURRENT_TIMESTAMP
            WHERE batch_id = ? AND current_sequence > ?
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
        return Map.of(
            "batchItemId", batchItemId,
            "itemStatus", "PENDING",
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
    }

    private record RiderBatchItemContext(
        long batchItemId,
        long batchId,
        int currentSequence,
        String itemStatus
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
            "SELECT id FROM meal_wallets WHERE customer_id = ? AND active = TRUE",
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

    private int remainingMealsForUpdate(long walletId) {
        Integer count = jdbcTemplate.query(
            "SELECT total_meals - reserved_meals - consumed_meals AS remaining FROM meal_wallets WHERE id = ? FOR UPDATE",
            ps -> ps.setLong(1, walletId),
            rs -> rs.next() ? rs.getInt("remaining") : null
        );
        return count == null ? 0 : count;
    }

    private void ensureNoDuplicateMealOrder(long customerId, LocalDate serveDate, String mealPeriod) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE do.customer_id = ?
              AND do.serve_date = ?
              AND mso.meal_period = ?
              AND mso.status <> 'CANCELLED'
            """, Integer.class, customerId, serveDate, mealPeriod);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED, "当前餐次已经下过单，请勿重复提交");
        }
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
            Map<String, Object> customer = jdbcTemplate.queryForMap("SELECT name, phone FROM customers WHERE id = ?", customerId);
            return insertAndReturnId(
                "INSERT INTO customer_addresses (customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (?, ?, ?, ?, ?, FALSE)",
                customerId,
                String.valueOf(customer.get("name")),
                String.valueOf(customer.get("phone")),
                deliveryAddress,
                deliveryAddress.contains("高新区") ? "高新区" : "老城区"
            );
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

    private String normalizeNote(String note) {
        return isBlank(note) ? "-" : note.trim();
    }

    private String normalizeCustomerRemark(String note) {
        return isBlank(note) ? null : note.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

    private LocalDateTime resolveReceiptVisibleAt(long mealSlotOrderId, LocalDateTime deliveredDateTime) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
            SELECT do.serve_date, mso.meal_period
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE mso.id = ?
            """, mealSlotOrderId);
        LocalDate serveDate = ((java.sql.Date) row.get("serve_date")).toLocalDate();
        String mealPeriod = String.valueOf(row.get("meal_period"));
        LocalDateTime threshold = "LUNCH".equals(mealPeriod)
            ? LocalDateTime.of(serveDate, LocalTime.of(11, 30))
            : LocalDateTime.of(serveDate, LocalTime.of(17, 0));
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
        
        return Map.of(
            "exceptionId", exceptionId,
            "status", "REPORTED",
            "message", "异常已上报，请等待处理"
        );
    }

    @Override
    public PageResponse<RiderTaskItemResponse> riderCompletedToday(String riderName) {
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
              AND DATE(dr.delivered_at) = CURDATE()
              AND da.status = 'DELIVERED'
            ORDER BY dr.delivered_at DESC
            """, (rs, rowNum) -> new RiderTaskItemResponse(
            rs.getLong("dispatch_id"),
            rs.getLong("meal_slot_order_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("delivery_address"),
            rs.getString("meal_period"),
            rs.getString("meal_name"),
            rs.getString("note"),
            rs.getString("delivery_status"),
            rs.getString("receipt_status"),
            rs.getString("receipt_url")
        ), riderName);
        return PageResponse.of(items, 1, 20, items.size());
    }

    @Override
    public Map<String, Object> revertOrderStatus(long mealSlotOrderId, String riderName) {
        return extension.revertOrderStatus(mealSlotOrderId, riderName);
    }

    @Override
    public Map<String, Object> saveOrderSequence(String riderName, String mealPeriod, List<Long> batchItemIds) {
        return extension.saveOrderSequence(riderName, mealPeriod, batchItemIds);
    }

    private List<String> parseBannerImages(Object bannerImages) {
        if (bannerImages == null || String.valueOf(bannerImages).isBlank() || "null".equals(String.valueOf(bannerImages))) {
            return List.of("../../assets/green-intro.jpg");
        }
        try {
            return objectMapper.readValue(String.valueOf(bannerImages), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of("../../assets/green-intro.jpg");
        }
    }
}
