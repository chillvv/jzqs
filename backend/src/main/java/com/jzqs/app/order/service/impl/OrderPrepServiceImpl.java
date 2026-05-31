package com.jzqs.app.order.service.impl;

import com.jzqs.app.admin.persistence.AdminRowMappers;
import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.order.api.ManualCreateCustomerAddressResponse;
import com.jzqs.app.order.api.ManualCreateCustomerSearchResponse;
import com.jzqs.app.order.api.OrderPrepItemResponse;
import com.jzqs.app.order.api.OrderPrepStatsResponse;
import com.jzqs.app.order.api.SubscriptionConfirmationItem;
import com.jzqs.app.order.api.SubscriptionPreviewItem;
import com.jzqs.app.order.api.SpecialOrderItem;
import com.jzqs.app.order.service.OrderPrepService;
import com.jzqs.app.subscription.api.SubscriptionPreviewCheckResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPrepServiceImpl implements OrderPrepService {
    private final JdbcTemplate jdbcTemplate;

    public OrderPrepServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OrderPrepStatsResponse prepStats() {
        Integer totalMeals = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM meal_slot_orders",
            Integer.class
        );
        Integer lunchCount = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM meal_slot_orders WHERE meal_period = 'LUNCH'",
            Integer.class
        );
        Integer dinnerCount = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM meal_slot_orders WHERE meal_period = 'DINNER'",
            Integer.class
        );
        Integer selfOrderCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM daily_orders WHERE source = 'MINIAPP'",
            Integer.class
        );
        Integer staffOrderCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM daily_orders WHERE source = 'BACKEND'",
            Integer.class
        );
        Integer subscriptionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE confirmed_from_subscription = TRUE",
            Integer.class
        );
        Integer specialOrderCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE user_note IS NOT NULL OR admin_note IS NOT NULL OR is_priority = TRUE OR special_tag IS NOT NULL",
            Integer.class
        );
        Integer adminRemarkCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE admin_note IS NOT NULL AND admin_note <> ''",
            Integer.class
        );
        Integer labelRequiredCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE special_tag IS NOT NULL AND special_tag <> ''",
            Integer.class
        );
        return new OrderPrepStatsResponse(
            nvl(totalMeals),
            nvl(lunchCount),
            nvl(dinnerCount),
            nvl(selfOrderCount),
            nvl(staffOrderCount),
            nvl(subscriptionCount),
            nvl(specialOrderCount),
            nvl(adminRemarkCount),
            nvl(labelRequiredCount)
        );
    }

    @Override
    public PageResponse<OrderPrepItemResponse> prepPage(String serveDate) {
        LocalDate targetDate;
        if (serveDate == null || serveDate.isEmpty()) {
            targetDate = LocalDate.now().plusDays(1);
        } else {
            targetDate = LocalDate.parse(serveDate);
        }
        
        String sql = """
            SELECT
                mso.id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                CASE
                    WHEN ms.meal_name IS NULL THEN CASE WHEN mso.meal_period = 'LUNCH' THEN '午餐 / 待配置菜品' ELSE '晚餐 / 待配置菜品' END
                    WHEN mso.meal_period = 'LUNCH' THEN CONCAT('午餐 / ', ms.meal_name)
                    ELSE CONCAT('晚餐 / ', ms.meal_name)
                END AS meal_summary,
                mso.quantity,
                COALESCE(mso.user_note, mso.note, '-') AS user_note,
                COALESCE(mso.admin_note, '') AS admin_note,
                COALESCE(mso.special_tag, '') AS special_tag,
                ca.address_line AS delivery_address,
                do.source,
                CASE WHEN c.is_priority_customer = TRUE OR mso.is_priority = TRUE THEN TRUE ELSE FALSE END AS priority_customer,
                CASE WHEN mso.confirmed_from_subscription = TRUE THEN TRUE ELSE FALSE END AS fixed_subscription,
                mso.status,
                CASE
                    WHEN EXISTS (
                        SELECT 1
                        FROM aftersale_cases ac2
                        WHERE ac2.meal_slot_order_id = mso.id
                          AND ac2.issue_type = 'REFUND'
                          AND ac2.status IN ('PENDING', 'PROCESSING', 'APPROVED')
                    ) THEN 'REFUND_PROCESSING'
                    ELSE mso.status
                END AS display_status,
                CASE
                    WHEN EXISTS (
                        SELECT 1
                        FROM aftersale_cases ac2
                        WHERE ac2.meal_slot_order_id = mso.id
                          AND ac2.issue_type = 'REFUND'
                          AND ac2.status IN ('PENDING', 'PROCESSING', 'APPROVED')
                    ) THEN '退款处理中'
                    WHEN mso.status = 'DELIVERED' THEN '已完成'
                    WHEN mso.status = 'DISPATCHING' THEN '配送中'
                    WHEN mso.status = 'REFUNDED' THEN '已退款'
                    WHEN mso.status = 'CANCELLED' THEN '已取消'
                    ELSE '待配送'
                END AS display_status_label,
                CASE WHEN mso.status = 'PENDING_DISPATCH' THEN TRUE ELSE FALSE END AS can_assign,
                CASE WHEN mso.status NOT IN ('CANCELLED', 'DELIVERED') THEN TRUE ELSE FALSE END AS can_cancel,
                CASE WHEN mso.status = 'DISPATCHING' THEN TRUE ELSE FALSE END AS can_receipt,
                CASE
                    WHEN mso.status = 'CANCELLED' THEN '已释放餐次'
                    WHEN mso.status = 'DELIVERED' THEN '已核销'
                    ELSE '已占用'
                END AS wallet_status_label
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN menu_week_items ms ON ms.serve_date = do.serve_date
                AND ms.meal_period = mso.meal_period
                AND ms.slot_status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM menu_weeks mw2 WHERE mw2.id = ms.week_id AND mw2.status = 'PUBLISHED')
            WHERE do.serve_date = ?
              AND mso.status <> 'REFUNDED'
              AND NOT EXISTS (
                SELECT 1
                FROM aftersale_cases ac
                WHERE ac.meal_slot_order_id = mso.id
                  AND ac.refund_blocking = TRUE
                  AND ac.status = 'COMPLETED'
              )
            ORDER BY mso.id
            """;
        List<OrderPrepItemResponse> items = jdbcTemplate.query(sql, AdminRowMappers.ORDER_PREP_ITEM, targetDate);
        return PageResponse.of(items, 1, 20, items.size());
    }

    @Override
    public List<SubscriptionConfirmationItem> subscriptionConfirmations(String serveDate) {
        String sql = """
            SELECT
                sc.id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                sc.meal_period,
                sc.quantity,
                ca.address_line,
                sc.user_note,
                sc.admin_note,
                sc.is_priority,
                sc.status
            FROM subscription_confirmations sc
            JOIN customers c ON c.id = sc.customer_id
            LEFT JOIN customer_addresses ca ON ca.id = sc.address_id
            WHERE sc.serve_date = ?
            ORDER BY sc.id
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SubscriptionConfirmationItem(
            rs.getLong("id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("meal_period"),
            rs.getInt("quantity"),
            rs.getString("address_line"),
            rs.getString("user_note"),
            rs.getString("admin_note"),
            rs.getBoolean("is_priority"),
            rs.getString("status")
        ), java.sql.Date.valueOf(serveDate));
    }

    @Override
    @Transactional
    public Map<String, Object> confirmSubscription(long confirmationId) {
        Integer updated = jdbcTemplate.update(
            "UPDATE subscription_confirmations SET status = 'CONFIRMED', confirmed_by = ?, confirmed_at = CURRENT_TIMESTAMP WHERE id = ?",
            "后台客服",
            confirmationId
        );
        if (updated == 0) {
            throw new com.jzqs.app.common.error.BusinessException(com.jzqs.app.common.error.ErrorCode.SUBSCRIPTION_CONFIRMATION_NOT_FOUND, "待确认记录不存在");
        }
        return Map.of("confirmationId", confirmationId, "status", "CONFIRMED");
    }

    @Override
    @Transactional
    public Map<String, Object> cancelSubscription(long confirmationId, String cancelReason) {
        Integer updated = jdbcTemplate.update(
            "UPDATE subscription_confirmations SET status = 'CANCELLED', cancel_reason = ? WHERE id = ?",
            cancelReason,
            confirmationId
        );
        if (updated == 0) {
            throw new com.jzqs.app.common.error.BusinessException(com.jzqs.app.common.error.ErrorCode.SUBSCRIPTION_CONFIRMATION_NOT_FOUND, "待确认记录不存在");
        }
        return Map.of("confirmationId", confirmationId, "status", "CANCELLED");
    }

    @Override
    @Transactional
    public Map<String, Object> updateAdminNote(long orderId, String adminNote, String specialTag) {
        Integer updated = jdbcTemplate.update(
            "UPDATE meal_slot_orders SET admin_note = ?, special_tag = ? WHERE id = ?",
            adminNote,
            specialTag,
            orderId
        );
        if (updated == 0) {
            throw new com.jzqs.app.common.error.BusinessException(com.jzqs.app.common.error.ErrorCode.ORDER_STATUS_INVALID, "订单不存在");
        }
        return Map.of("orderId", orderId, "status", "UPDATED");
    }

    @Override
    @Transactional
    public Map<String, Object> updateOrderProfile(long orderId, Map<String, Object> payload) {
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
                SELECT
                    do.customer_id,
                    mso.meal_period,
                    mso.quantity,
                    mso.address_id,
                    mso.status,
                    COALESCE(ca.address_line, '') AS delivery_address,
                    COALESCE(mso.admin_note, '') AS admin_note,
                    COALESCE(mso.special_tag, '') AS special_tag,
                    COALESCE(mso.is_priority, FALSE) AS is_priority
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                LEFT JOIN customer_addresses ca ON ca.id = mso.address_id
                WHERE mso.id = ?
                """,
            orderId
        );
        if (records.isEmpty()) {
            throw new com.jzqs.app.common.error.BusinessException(com.jzqs.app.common.error.ErrorCode.ORDER_STATUS_INVALID, "订单不存在");
        }

        Map<String, Object> current = records.get(0);
        long customerId = ((Number) current.get("customer_id")).longValue();
        String currentMealPeriod = stringValue(current.get("meal_period"));
        int quantity = intValue(payload.get("quantity"), ((Number) current.get("quantity")).intValue());
        String deliveryAddress = fallbackString(payload.get("deliveryAddress"), stringValue(current.get("delivery_address")));
        String adminNote = payload.containsKey("adminNote")
            ? stringValue(payload.get("adminNote"))
            : stringValue(current.get("admin_note"));
        String specialTag = payload.containsKey("specialTag")
            ? stringValue(payload.get("specialTag"))
            : stringValue(current.get("special_tag"));
        boolean isPriority = payload.containsKey("priorityCustomer")
            ? booleanValue(payload.get("priorityCustomer"), false)
            : booleanValue(current.get("is_priority"), false);
        String status = payload.containsKey("status")
            ? stringValue(payload.get("status"))
            : stringValue(current.get("status"));
        String mealPeriod = resolveMealPeriod(payload.get("mealPeriod"), payload.get("mealSummary"), currentMealPeriod);
        long addressId = ensureCustomerAddress(customerId, deliveryAddress);

        Integer updated = jdbcTemplate.update(
            "UPDATE meal_slot_orders SET meal_period = ?, quantity = ?, address_id = ?, admin_note = ?, special_tag = ?, is_priority = ?, status = ? WHERE id = ?",
            mealPeriod,
            Math.max(1, quantity),
            addressId,
            adminNote,
            specialTag,
            isPriority,
            status,
            orderId
        );
        if (updated == 0) {
            throw new com.jzqs.app.common.error.BusinessException(com.jzqs.app.common.error.ErrorCode.ORDER_STATUS_INVALID, "订单不存在");
        }
        return Map.of("orderId", orderId, "status", "UPDATED");
    }

    @Override
    public List<SpecialOrderItem> specialOrders(String serveDate) {
        String sql = """
            SELECT
                mso.id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                ca.address_line,
                mso.meal_period,
                mso.quantity,
                COALESCE(mso.user_note, '-') AS user_note,
                COALESCE(mso.admin_note, '') AS admin_note,
                COALESCE(mso.special_tag, '') AS special_tag,
                CASE WHEN c.is_priority_customer = TRUE OR mso.is_priority = TRUE THEN TRUE ELSE FALSE END AS priority_customer
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            LEFT JOIN customer_addresses ca ON ca.id = mso.address_id
            WHERE do.serve_date = ?
              AND (
                    mso.user_note IS NOT NULL
                 OR mso.admin_note IS NOT NULL
                 OR mso.special_tag IS NOT NULL
                 OR c.is_priority_customer = TRUE
                 OR mso.is_priority = TRUE
              )
            ORDER BY mso.id
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SpecialOrderItem(
            rs.getLong("id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("address_line"),
            rs.getString("meal_period"),
            rs.getInt("quantity"),
            rs.getString("user_note"),
            rs.getString("admin_note"),
            rs.getString("special_tag"),
            rs.getBoolean("priority_customer")
        ), java.sql.Date.valueOf(serveDate));
    }

    @Override
    public List<ManualCreateCustomerSearchResponse> searchManualCreateCustomers(String keyword) {
        String normalizedKeyword = stringValue(keyword);
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        String likeKeyword = "%" + normalizedKeyword + "%";
        List<Map<String, Object>> customerRows = jdbcTemplate.queryForList(
            """
                SELECT c.id, c.name, c.phone,
                       COALESCE((SELECT mw.total_meals - mw.reserved_meals - mw.consumed_meals FROM meal_wallets mw WHERE mw.customer_id = c.id AND mw.active = TRUE), 0) AS remaining_meals
                FROM customers c
                WHERE c.active = TRUE
                  AND (c.name LIKE ? OR c.phone LIKE ?)
                ORDER BY CASE
                    WHEN c.phone = ? THEN 0
                    WHEN c.phone LIKE ? THEN 1
                    WHEN c.name = ? THEN 2
                    ELSE 3
                END, c.id DESC
                LIMIT 20
                """,
            likeKeyword,
            likeKeyword,
            normalizedKeyword,
            likeKeyword,
            normalizedKeyword
        );
        if (customerRows.isEmpty()) {
            return List.of();
        }

        List<Long> customerIds = customerRows.stream()
            .map(row -> ((Number) row.get("id")).longValue())
            .toList();
        Map<Long, List<ManualCreateCustomerAddressResponse>> addressesByCustomerId = new LinkedHashMap<>();
        customerIds.forEach(customerId -> addressesByCustomerId.put(customerId, new ArrayList<>()));

        String placeholders = String.join(",", customerIds.stream().map(id -> "?").toList());
        List<Map<String, Object>> addressRows = jdbcTemplate.queryForList(
            """
                SELECT id, customer_id, address_line, COALESCE(area_code, '') AS area_code, is_default
                FROM customer_addresses
                WHERE customer_id IN ("""
                + placeholders
                + """
                )
                ORDER BY customer_id, is_default DESC, id ASC
                """,
            customerIds.toArray()
        );
        for (Map<String, Object> row : addressRows) {
            long customerId = ((Number) row.get("customer_id")).longValue();
            addressesByCustomerId.computeIfAbsent(customerId, ignored -> new ArrayList<>()).add(
                new ManualCreateCustomerAddressResponse(
                    ((Number) row.get("id")).longValue(),
                    stringValue(row.get("address_line")),
                    stringValue(row.get("area_code")),
                    booleanValue(row.get("is_default"), false)
                )
            );
        }

        return customerRows.stream()
            .map(row -> {
                long customerId = ((Number) row.get("id")).longValue();
                int remainingMeals = ((Number) row.get("remaining_meals")).intValue();
                return new ManualCreateCustomerSearchResponse(
                    customerId,
                    stringValue(row.get("name")),
                    stringValue(row.get("phone")),
                    remainingMeals,
                    List.copyOf(addressesByCustomerId.getOrDefault(customerId, List.of()))
                );
            })
            .toList();
    }

    @Override
    public List<SubscriptionPreviewItem> subscriptionPreview(String serveDate) {
        LocalDate targetDate = LocalDate.parse(serveDate);
        String sql = """
            SELECT 
                sr.customer_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                'LUNCH' AS meal_period,
                ca.id AS address_id,
                ca.address_line AS delivery_address,
                COALESCE(sr.default_note, '-') AS default_note,
                COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) AS remaining_meals,
                CASE WHEN COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) > 0 THEN TRUE ELSE FALSE END AS has_balance
            FROM subscription_rules sr
            JOIN customers c ON c.id = sr.customer_id
            LEFT JOIN meal_wallets mw ON mw.customer_id = sr.customer_id AND mw.active = TRUE
            LEFT JOIN customer_addresses ca ON ca.id = sr.default_address_id
            WHERE sr.active = TRUE 
              AND sr.paused = FALSE
              AND sr.lunch_enabled = TRUE
              AND sr.start_date <= ?
              AND sr.end_date >= ?
            
            UNION ALL
            
            SELECT 
                sr.customer_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                'DINNER' AS meal_period,
                ca.id AS address_id,
                ca.address_line AS delivery_address,
                COALESCE(sr.default_note, '-') AS default_note,
                COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) AS remaining_meals,
                CASE WHEN COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) > 0 THEN TRUE ELSE FALSE END AS has_balance
            FROM subscription_rules sr
            JOIN customers c ON c.id = sr.customer_id
            LEFT JOIN meal_wallets mw ON mw.customer_id = sr.customer_id AND mw.active = TRUE
            LEFT JOIN customer_addresses ca ON ca.id = sr.default_address_id
            WHERE sr.active = TRUE 
              AND sr.paused = FALSE
              AND sr.dinner_enabled = TRUE
              AND sr.start_date <= ?
              AND sr.end_date >= ?
            
            ORDER BY customer_id, meal_period
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SubscriptionPreviewItem(
            rs.getLong("customer_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("meal_period"),
            rs.getLong("address_id"),
            rs.getString("delivery_address"),
            rs.getString("default_note"),
            rs.getInt("remaining_meals"),
            rs.getBoolean("has_balance")
        ), targetDate, targetDate, targetDate, targetDate);
    }

    @Override
    public SubscriptionPreviewCheckResponse subscriptionPreviewCheck(String serveDate) {
        List<SubscriptionPreviewItem> previewItems = subscriptionPreview(serveDate);
        
        List<SubscriptionPreviewCheckResponse.InsufficientCustomer> insufficientCustomers = new ArrayList<>();
        int sufficientCount = 0;
        
        for (SubscriptionPreviewItem item : previewItems) {
            if (!item.hasBalance()) {
                insufficientCustomers.add(new SubscriptionPreviewCheckResponse.InsufficientCustomer(
                    item.customerId(),
                    item.customerName(),
                    item.customerPhone(),
                    item.remainingMeals(),
                    1, // requiredMeals
                    item.mealPeriod()
                ));
            } else {
                sufficientCount++;
            }
        }
        
        return new SubscriptionPreviewCheckResponse(
            previewItems.size(),
            sufficientCount,
            insufficientCustomers.size(),
            insufficientCustomers
        );
    }

    @Override
    @Transactional
    public Map<String, Object> bulkImportSubscription(String serveDate, List<com.jzqs.app.order.api.SubscriptionImportItem> items) {
        LocalDate targetDate = LocalDate.parse(serveDate);
        int successCount = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        
        for (com.jzqs.app.order.api.SubscriptionImportItem item : items) {
            // 检查余额
            Integer remainingMeals = jdbcTemplate.queryForObject(
                "SELECT COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) FROM meal_wallets mw WHERE mw.customer_id = ? AND mw.active = TRUE",
                Integer.class,
                item.customerId()
            );
            
            if (remainingMeals == null || remainingMeals <= 0) {
                String customerName = jdbcTemplate.queryForObject(
                    "SELECT name FROM customers WHERE id = ?",
                    String.class,
                    item.customerId()
                );
                failures.add(Map.of(
                    "customerId", item.customerId(),
                    "customerName", customerName != null ? customerName : "未知",
                    "reason", "余额不足",
                    "remainingMeals", remainingMeals != null ? remainingMeals : 0,
                    "requiredMeals", 1
                ));
                continue;
            }
            
            try {
                String addressLine = jdbcTemplate.queryForObject("SELECT address_line FROM customer_addresses WHERE id = ?", String.class, item.addressId());
                manualCreateWithDate(item.customerId(), item.mealPeriod(), item.note(), addressLine, "SUBSCRIPTION", targetDate, 1);
                successCount++;
            } catch (Exception e) {
                String customerName = jdbcTemplate.queryForObject(
                    "SELECT name FROM customers WHERE id = ?",
                    String.class,
                    item.customerId()
                );
                failures.add(Map.of(
                    "customerId", item.customerId(),
                    "customerName", customerName != null ? customerName : "未知",
                    "reason", e.getMessage()
                ));
            }
        }
        
        return Map.of(
            "successCount", successCount,
            "failureCount", failures.size(),
            "failures", failures
        );
    }

    @Override
    @Transactional
    public Map<String, Object> manualCreate(long customerId, Long addressId, String mealPeriod, String note, String deliveryAddress, String source, int quantity, String serveDate) {
        LocalDate date = serveDate == null || serveDate.isBlank() 
            ? LocalDate.now() 
            : LocalDate.parse(serveDate);
            
        return manualCreateWithDate(
            customerId,
            mealPeriod,
            note,
            resolveManualCreateDeliveryAddress(customerId, addressId, deliveryAddress),
            source,
            date,
            quantity
        );
    }

    @Transactional
    private Map<String, Object> manualCreateWithDate(long customerId, String mealPeriod, String note, String deliveryAddress, String source, LocalDate serveDate, int quantity) {
        long addressId = ensureCustomerAddress(customerId, deliveryAddress);
        
        Long walletId = jdbcTemplate.query(
            "SELECT id FROM meal_wallets WHERE customer_id = ? AND active = TRUE",
            ps -> ps.setLong(1, customerId),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (walletId == null) {
            throw new com.jzqs.app.common.error.BusinessException(com.jzqs.app.common.error.ErrorCode.WALLET_BALANCE_NOT_ENOUGH, "该客户未开通套餐或套餐已过期");
        }
        Integer remainingMeals = jdbcTemplate.query(
            "SELECT (total_meals - reserved_meals - consumed_meals) FROM meal_wallets WHERE id = ?",
            ps -> ps.setLong(1, walletId),
            rs -> rs.next() ? rs.getInt(1) : 0
        );
        if (remainingMeals < quantity) {
            throw new com.jzqs.app.common.error.BusinessException(com.jzqs.app.common.error.ErrorCode.INSUFFICIENT_MEALS, "该客户剩余餐次不足（仅剩 " + remainingMeals + " 餐）");
        }
        
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
                ps.setObject(2, serveDate);
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
        
        long dailyOrderId = existingDailyOrderId == null
            ? insertAndReturnId(
                "INSERT INTO daily_orders (customer_id, serve_date, source, status, locked, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                customerId, serveDate, source, "PENDING_DISPATCH", false, Timestamp.valueOf(LocalDateTime.now())
            )
            : existingDailyOrderId;
            
        String normalizedMealPeriod = normalizeMealPeriod(mealPeriod);

        Long mergeTargetOrderId = findMergeTargetOrderId(customerId, serveDate, normalizedMealPeriod, addressId);
        if (mergeTargetOrderId != null) {
            Map<String, Object> existingOrder = jdbcTemplate.queryForMap(
                "SELECT COALESCE(user_note, note, '-') AS user_note, COALESCE(note, '-') AS note FROM meal_slot_orders WHERE id = ?",
                mergeTargetOrderId
            );
            String mergedNote = mergeOrderNote(stringValue(existingOrder.get("user_note")), note);
            jdbcTemplate.update(
                "UPDATE meal_slot_orders SET quantity = quantity + ?, note = ?, user_note = ? WHERE id = ?",
                quantity,
                mergedNote,
                mergedNote,
                mergeTargetOrderId
            );
            jdbcTemplate.update("UPDATE meal_wallets SET reserved_meals = reserved_meals + ? WHERE id = ?", quantity, walletId);
            insertWalletTransaction(
                walletId,
                "RESERVE",
                -quantity,
                "系统",
                "SUBSCRIPTION".equals(source) ? "固定订餐自动扣餐" : "代客录单加餐占用餐次",
                mergeTargetOrderId
            );
            return Map.of("orderId", mergeTargetOrderId, "status", "MERGED");
        }

        long mealSlotOrderId = insertAndReturnId(
            "INSERT INTO meal_slot_orders (daily_order_id, meal_period, quantity, address_id, note, user_note, status, source_type, confirmed_from_subscription) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            dailyOrderId, normalizedMealPeriod, quantity, addressId, note, note, "PENDING_DISPATCH", source, "SUBSCRIPTION".equals(source)
        );
        jdbcTemplate.update("UPDATE meal_wallets SET reserved_meals = reserved_meals + ? WHERE id = ?", quantity, walletId);
        insertWalletTransaction(
            walletId,
            "RESERVE",
            -quantity,
            "系统",
            "SUBSCRIPTION".equals(source) ? "固定订餐自动扣餐" : "代客录单占用餐次",
            mealSlotOrderId
        );
        return Map.of("orderId", mealSlotOrderId, "status", "PENDING_DISPATCH");
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

    @Override
    @Transactional
    public Map<String, Object> cancelOrder(long orderId) {
        Integer updated = jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'CANCELLED' WHERE id = ? AND status <> 'CANCELLED'", orderId);
        if (updated == 0) {
            return Map.of("orderId", orderId, "status", "NOT_FOUND");
        }

        // Remove from dispatch center and rider queues
        List<Long> batchIds = jdbcTemplate.query(
            "SELECT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
            (rs, rowNum) -> rs.getLong("batch_id"),
            orderId
        );
        if (!batchIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE meal_slot_order_id = ?", orderId);
            long batchId = batchIds.get(0);
            jdbcTemplate.update(
                "UPDATE dispatch_batches SET total_count = (SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ?), delivered_count = (SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ? AND item_status = 'DELIVERED') WHERE id = ?",
                batchId, batchId, batchId
            );
        }
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE meal_slot_order_id = ?", orderId);

        Map<String, Object> orderInfo = jdbcTemplate.queryForMap("""
            SELECT mw.id as wallet_id, mso.quantity
            FROM meal_wallets mw
            JOIN daily_orders do ON do.customer_id = mw.customer_id
            JOIN meal_slot_orders mso ON mso.daily_order_id = do.id
            WHERE mso.id = ? AND mw.active = TRUE
            """, orderId);
        Long walletId = ((Number) orderInfo.get("wallet_id")).longValue();
        int quantity = ((Number) orderInfo.get("quantity")).intValue();
        
        jdbcTemplate.update("UPDATE meal_wallets SET reserved_meals = CASE WHEN reserved_meals >= ? THEN reserved_meals - ? ELSE 0 END WHERE id = ?", quantity, quantity, walletId);
        insertWalletTransaction(walletId, "RELEASE", quantity, "系统", "取消订单释放餐次", orderId);
        return Map.of("orderId", orderId, "status", "CANCELLED");
    }

    @Override
    @Transactional
    public Map<String, Object> deleteOrder(long orderId) {
        // 1. 检查订单是否存在
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE id = ?", 
            Integer.class, 
            orderId
        );
        if (count == null || count == 0) {
            return Map.of("orderId", orderId, "status", "NOT_FOUND");
        }

        // 2. 获取订单信息用于后续清理
        Map<String, Object> orderInfo = jdbcTemplate.queryForMap("""
            SELECT mso.id, mso.daily_order_id, mso.status, mso.quantity, do.customer_id
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE mso.id = ?
            """, orderId);
        
        long dailyOrderId = ((Number) orderInfo.get("daily_order_id")).longValue();
        long customerId = ((Number) orderInfo.get("customer_id")).longValue();
        String status = (String) orderInfo.get("status");
        int quantity = ((Number) orderInfo.get("quantity")).intValue();

        // 3. 如果订单未取消，先释放餐次余额
        if (!"CANCELLED".equals(status)) {
            Long walletId = jdbcTemplate.queryForObject(
                "SELECT id FROM meal_wallets WHERE customer_id = ? AND active = TRUE", 
                Long.class, 
                customerId
            );
            if (walletId != null) {
                jdbcTemplate.update(
                    "UPDATE meal_wallets SET reserved_meals = CASE WHEN reserved_meals >= ? THEN reserved_meals - ? ELSE 0 END WHERE id = ?", 
                    quantity, quantity, walletId
                );
                insertWalletTransaction(walletId, "RELEASE", quantity, "系统", "删除订单释放餐次", orderId);
            }
        }

        // 4. 删除配送相关记录
        List<Long> batchIds = jdbcTemplate.query(
            "SELECT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
            (rs, rowNum) -> rs.getLong("batch_id"),
            orderId
        );
        if (!batchIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE meal_slot_order_id = ?", orderId);
            for (long batchId : batchIds) {
                jdbcTemplate.update("""
                    UPDATE dispatch_batches 
                    SET total_count = (SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ?), 
                        delivered_count = (SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ? AND item_status = 'DELIVERED') 
                    WHERE id = ?
                    """, batchId, batchId, batchId);
            }
        }
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE meal_slot_order_id = ?", orderId);

        // 5. 删除配送回执
        jdbcTemplate.update("DELETE FROM delivery_receipts WHERE meal_slot_order_id = ?", orderId);

        // 6. 删除订单本身
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id = ?", orderId);

        // 7. 检查 daily_order 是否还有其他餐次，如果没有则删除
        Integer remainingSlots = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE daily_order_id = ?", 
            Integer.class, 
            dailyOrderId
        );
        if (remainingSlots != null && remainingSlots == 0) {
            jdbcTemplate.update("DELETE FROM daily_orders WHERE id = ?", dailyOrderId);
        }

        return Map.of("orderId", orderId, "status", "DELETED");
    }

    @Override
    @Transactional
    public BatchOperationResponse consumeOrders(List<Long> orderIds) {
        int successCount = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Long orderId : orderIds) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM meal_slot_orders WHERE id = ? AND status = 'DELIVERED'", Integer.class, orderId);
            if (count == null || count == 0) {
                failures.add(Map.of("targetId", orderId, "code", "ORDER_NOT_DELIVERED", "message", "订单未送达，不能核销"));
                continue;
            }
            successCount++;
        }
        return new BatchOperationResponse(successCount, failures.size(), failures);
    }

    private long ensureCustomerAddress(long customerId, String deliveryAddress) {
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

    private String resolveManualCreateDeliveryAddress(long customerId, Long addressId, String deliveryAddress) {
        if (addressId != null && addressId > 0) {
            List<String> addressLines = jdbcTemplate.queryForList(
                "SELECT address_line FROM customer_addresses WHERE id = ? AND customer_id = ?",
                String.class,
                addressId,
                customerId
            );
            if (!addressLines.isEmpty()) {
                return addressLines.get(0);
            }
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该客户地址");
        }

        String normalizedAddress = stringValue(deliveryAddress);
        if (normalizedAddress.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择配送地址");
        }
        return normalizedAddress;
    }

    private void insertWalletTransaction(long walletId, String transactionType, int mealDelta, String operatorName, String remark, Long relatedOrderId) {
        jdbcTemplate.update(
            "INSERT INTO wallet_transactions (wallet_id, transaction_type, meal_delta, operator_name, remark, created_at, related_order_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
            walletId,
            transactionType,
            mealDelta,
            operatorName,
            remark,
            Timestamp.valueOf(LocalDateTime.now()),
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

    private String resolveMealPeriod(Object mealPeriodValue, Object legacyMealSummaryValue, String fallbackMealPeriod) {
        String normalizedMealPeriod = normalizeMealPeriod(stringValue(mealPeriodValue));
        if (!normalizedMealPeriod.isBlank()) {
            return normalizedMealPeriod;
        }
        String mealSummary = stringValue(legacyMealSummaryValue);
        if (mealSummary.isBlank()) {
            return fallbackMealPeriod;
        }
        return mealSummary.contains("晚餐") ? "DINNER" : "LUNCH";
    }

    private String normalizeMealPeriod(String mealPeriod) {
        if ("DINNER".equalsIgnoreCase(mealPeriod) || mealPeriod.contains("晚餐")) {
            return "DINNER";
        }
        if ("LUNCH".equalsIgnoreCase(mealPeriod) || mealPeriod.contains("午餐")) {
            return "LUNCH";
        }
        return "";
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String fallbackString(Object primaryValue, String fallback) {
        String normalized = stringValue(primaryValue);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String mergeOrderNote(String existingNote, String newNote) {
        String current = stringValue(existingNote);
        String incoming = stringValue(newNote);
        if (incoming.isBlank() || "-".equals(incoming)) {
            return current.isBlank() ? "-" : current;
        }
        if (current.isBlank() || "-".equals(current)) {
            return incoming;
        }
        if (current.contains(incoming)) {
            return current;
        }
        return current + " / " + incoming;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim()) || "1".equals(text.trim());
        }
        return fallback;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
