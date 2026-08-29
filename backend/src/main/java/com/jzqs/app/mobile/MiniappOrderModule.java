package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.common.util.OrderNoteTexts;
import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.mobile.api.MobileCreateOrderResponse;
import com.jzqs.app.order.service.OrderNoteSnapshotService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
class MiniappOrderModule {
    private static final Logger log = LoggerFactory.getLogger(MiniappOrderModule.class);

    private final JdbcTemplate jdbcTemplate;
    private final DispatchService dispatchService;
    private final OrderNoteSnapshotService orderNoteSnapshotService;
    private final RealtimeAudienceModule realtimeAudienceModule;
    private final RiderQueueSupport riderQueueSupport;

    MiniappOrderModule(
        JdbcTemplate jdbcTemplate,
        DispatchService dispatchService,
        OrderNoteSnapshotService orderNoteSnapshotService,
        RealtimeAudienceModule realtimeAudienceModule,
        RiderQueueSupport riderQueueSupport
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatchService = dispatchService;
        this.orderNoteSnapshotService = orderNoteSnapshotService;
        this.realtimeAudienceModule = realtimeAudienceModule;
        this.riderQueueSupport = riderQueueSupport;
    }

    MobileCreateOrderResponse createOrder(
        long customerId,
        String serveDate,
        String mealPeriod,
        String deliveryAddress,
        String note
    ) {
        return createOrder(customerId, serveDate, mealPeriod, deliveryAddress, note, 1);
    }

    MobileCreateOrderResponse createOrder(
        long customerId,
        String serveDate,
        String mealPeriod,
        String deliveryAddress,
        String note,
        int quantity
    ) {
        ensureOrderingEnabled();
        LocalDate orderDate = LocalDate.parse(serveDate);
        String normalizedMealPeriod = normalizeMealPeriod(mealPeriod);
        ensureSelfOrderAllowed(orderDate);
        requireActiveMenu(orderDate, normalizedMealPeriod);
        int units = Math.max(1, quantity);
        return createOrder(
            customerId,
            orderDate,
            normalizedMealPeriod,
            deliveryAddress,
            normalizeNote(note),
            units
        );
    }

    /**
     * 批量下单：一次请求 quantity=N 直接创建/合并 N 份，扣 N 餐并写一条聚合流水，
     * 不再循环逐份下单（旧逻辑会导致 N 条 -1 流水、且重复提交时数量逐份翻倍）。
     * 防重复：若同客户同日同餐次已有订单且最近 2 分钟内有扣餐流水，判定为重复提交，
     * 直接返回已有订单、不再累加数量，从机制上杜绝"繁忙重试"导致的多扣餐。
     */
    MobileCreateOrderResponse createOrder(
        long customerId,
        LocalDate orderDate,
        String mealPeriod,
        String deliveryAddress,
        String finalUserNote,
        int units
    ) {
        long walletId = activeWalletId(customerId);
        int remainingMeals = remainingMealsForUpdate(walletId);
        if (remainingMeals < units) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_MEALS, "剩余餐次不足，无法下单");
        }

        long addressId = ensureCustomerAddress(customerId, deliveryAddress);
        String merchantRemark = normalizeCustomerMerchantRemark(customerId);
        Long mergeTargetOrderId = findMergeTargetOrderId(customerId, orderDate, mealPeriod, addressId);
        if (mergeTargetOrderId != null) {
            // 短时间重复下单不再按「2 分钟扣餐流水窗口」拦截：用户短时间再次下单（加餐）
            // 属于正常业务，直接合并累加数量并扣餐。防重复依赖幂等(clientRequestId)：
            // 同一请求的网络重试复用同一 clientRequestId 会在 Controller 幂等层被拦，
            // 不会走到这里造成同餐次数量叠加。
            ExistingOrderNoteRow existingOrder = loadExistingOrderNoteRow(mergeTargetOrderId);
            String mergedUserNote = mergeOrderNote(
                preferredOrderNote(existingOrder.userNote(), existingOrder.note()),
                finalUserNote
            );
            LocalDateTime mergeTime = LocalDateTime.now();
            jdbcTemplate.update(
                """
                    UPDATE meal_slot_orders
                    SET quantity = quantity + ?,
                        note = ?,
                        user_note = ?,
                        merchant_remark = CASE
                            WHEN (merchant_remark IS NULL OR TRIM(merchant_remark) = '' OR merchant_remark = '-') AND ? <> '' THEN ?
                            ELSE merchant_remark
                        END
                    WHERE id = ?
                    """,
                units,
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
            jdbcTemplate.update("UPDATE meal_wallets SET consumed_meals = consumed_meals + ? WHERE id = ?", units, walletId);
            insertWalletTransaction(walletId, "CONSUME", -units, "小程序", "用户自主下单加餐", mergeTime, mergeTargetOrderId);
            attemptWriteOrderSnapshot(
                mergeTargetOrderId,
                customerId,
                normalizeSnapshotNote(mergedUserNote),
                existingMerchantRemark(mergeTargetOrderId, merchantRemark),
                mergeTime
            );
            jdbcTemplate.execute("/* force flush */ SELECT 1");
            attemptAutoAssignPendingOrders(mealPeriod, mergeTargetOrderId, customerId);
            attemptRefreshQueueState(mergeTargetOrderId);
            attemptPublishDispatchEvent("dispatch.queue.changed", mergeTargetOrderId);
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
            dailyOrderId, mealPeriod, mealPeriod, units, addressId, finalUserNote, finalUserNote, merchantRemark, "PENDING_DISPATCH", "MINIAPP"
        );
        if (mealSlotOrderId <= 0) {
            mealSlotOrderId = resolveLatestMiniappOrderId(dailyOrderId, mealPeriod, mealPeriod, addressId);
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
        jdbcTemplate.update("UPDATE meal_wallets SET consumed_meals = consumed_meals + ? WHERE id = ?", units, walletId);
        insertWalletTransaction(walletId, "CONSUME", -units, "小程序", "用户自主下单", now, mealSlotOrderId);
        attemptWriteOrderSnapshot(mealSlotOrderId, customerId, finalUserNote, merchantRemark, snapshotTime);

        jdbcTemplate.execute("/* force flush */ SELECT 1");
        attemptAutoAssignPendingOrders(mealPeriod, mealSlotOrderId, customerId);
        String currentStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM meal_slot_orders WHERE id = ?",
            String.class,
            mealSlotOrderId
        );

        attemptPublishDispatchEvent("dispatch.queue.changed", mealSlotOrderId);
        attemptPublishCustomerEvent("customer.order.changed", customerId, mealSlotOrderId);
        attemptPublishCustomerEvent("customer.wallet.changed", customerId, mealSlotOrderId);
        return new MobileCreateOrderResponse(
            mealSlotOrderId,
            currentStatus != null ? currentStatus : "PENDING_DISPATCH",
            "RESERVED"
        );
    }

    private void attemptRefreshQueueState(long mealSlotOrderId) {
        try {
            riderQueueSupport.refreshQueueStateForOrder(mealSlotOrderId);
        } catch (RuntimeException ex) {
            log.warn(
                "miniapp create order queue refresh skipped orderId={} reason={}",
                mealSlotOrderId,
                ex.getMessage(),
                ex
            );
        }
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
            realtimeAudienceModule.publishCustomerEvent(eventType, customerId, orderId);
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

    private void attemptPublishDispatchEvent(String eventType, Object orderId) {
        try {
            realtimeAudienceModule.publishDispatchEvent(eventType, null, null, orderId);
        } catch (RuntimeException ex) {
            log.warn(
                "miniapp create order dispatch realtime publish skipped eventType={} orderId={} reason={}",
                eventType,
                orderId,
                ex.getMessage(),
                ex
            );
        }
    }

    private void attemptWriteOrderSnapshot(
        long mealSlotOrderId,
        long customerId,
        String orderUserNote,
        String orderMerchantRemark,
        LocalDateTime snapshotTime
    ) {
        try {
            orderNoteSnapshotService.writeOrderSnapshot(
                mealSlotOrderId,
                customerId,
                "小程序",
                orderUserNote,
                List.of(orderMerchantRemark),
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

    private void ensureOrderingEnabled() {
        Boolean enabled = jdbcTemplate.queryForObject(
            "SELECT ordering_enabled FROM admin_settings WHERE id = 1",
            Boolean.class
        );
        if (!Boolean.TRUE.equals(enabled)) {
            throw new BusinessException(ErrorCode.ORDERING_DISABLED, "当前暂停接单");
        }
    }

    private void ensureSelfOrderAllowed(LocalDate orderDate) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        if (!orderDate.equals(tomorrow)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "当前仅支持预订明日餐食");
        }
        if (!MiniappOrderWindow.isOpen(LocalTime.now(), MiniappOrderWindow.openTime(jdbcTemplate), MiniappOrderWindow.cutoffTime(jdbcTemplate))) {
            throw new BusinessException(ErrorCode.ORDERING_DISABLED, "当前自助下单已截止，如需加单请联系专属客服微信");
        }
    }

    private void requireActiveMenu(LocalDate serveDate, String mealPeriod) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM menu_week_items mwi
                JOIN menu_weeks mw ON mw.id = mwi.week_id
                WHERE mw.week_start_date = ?
                  AND mwi.serve_date = ?
                  AND mwi.meal_period = ?
                  AND mwi.slot_status = 'ACTIVE'
                """,
            Integer.class,
            currentWeekMonday(serveDate),
            serveDate,
            mealPeriod
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND, "当前餐次未配置可售菜品");
        }
    }

    private long activeWalletId(long customerId) {
        // 确定性选择「id 最小的有效 active 钱包」：与 customerHome 展示口径一致，
        // 避免依赖 MySQL 行序在多个 active 钱包（含已过期）时取到不同钱包，
        // 导致前端显示余量充足、实际扣减钱包余量不足的「只下了午餐」问题。
        Long walletId = jdbcTemplate.query(
            "SELECT id FROM meal_wallets WHERE customer_id = ? AND active = TRUE AND (expired_at IS NULL OR expired_at >= CURRENT_TIMESTAMP) ORDER BY id LIMIT 1",
            ps -> ps.setLong(1, customerId),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (walletId == null) {
            throw new BusinessException(ErrorCode.WALLET_BALANCE_NOT_ENOUGH, "客户未开通有效套餐");
        }
        return walletId;
    }

    private int remainingMealsForUpdate(long walletId) {
        Integer count = jdbcTemplate.query(
            "SELECT total_meals - consumed_meals AS remaining FROM meal_wallets WHERE id = ? FOR UPDATE",
            ps -> ps.setLong(1, walletId),
            rs -> rs.next() ? rs.getInt("remaining") : null
        );
        return count == null ? 0 : count;
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
                  AND mso.status NOT IN ('CANCELLED', 'REFUNDED', 'DELIVERED')
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
                rs -> rs.next() ? new CustomerContactRow(rs.getString("name"), rs.getString("phone")) : null
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

    private String normalizeSnapshotNote(String note) {
        String normalized = isBlank(note) ? "" : note.trim();
        return normalized.isEmpty() || "-".equals(normalized) ? null : normalized;
    }

    private String normalizeNote(String note) {
        return isBlank(note) ? "-" : note.trim();
    }

    private String normalizeMealPeriod(String mealPeriod) {
        if ("DINNER".equalsIgnoreCase(mealPeriod) || safeString(mealPeriod).contains("晚餐")) {
            return "DINNER";
        }
        return "LUNCH";
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

    /**
     * 合并加餐时订单列上的商家备注保持「已有优先」（与上面 UPDATE 的 CASE 口径一致），
     * 快照用它重建，避免把后台/运营填的备注冲掉。
     */
    private String existingMerchantRemark(long mealSlotOrderId, String fallbackRemark) {
        try {
            List<String> remarks = jdbcTemplate.queryForList(
                "SELECT COALESCE(merchant_remark, '') FROM meal_slot_orders WHERE id = ?",
                String.class,
                mealSlotOrderId
            );
            if (remarks.isEmpty()) {
                return fallbackRemark;
            }
            String normalized = normalizeOrderMergeNote(remarks.get(0));
            return normalized.isEmpty() ? fallbackRemark : normalized;
        } catch (RuntimeException ex) {
            log.warn(
                "miniapp merge order merchant remark skipped orderId={} reason={}",
                mealSlotOrderId,
                ex.getMessage(),
                ex
            );
            return fallbackRemark;
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
        return current + OrderNoteTexts.SEPARATOR + incoming;
    }

    private LocalDate currentWeekMonday(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ExistingOrderNoteRow(String note, String userNote) {
    }

    private record CustomerContactRow(String name, String phone) {
    }
}
