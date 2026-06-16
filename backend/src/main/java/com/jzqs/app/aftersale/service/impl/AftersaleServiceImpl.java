package com.jzqs.app.aftersale.service.impl;

import com.jzqs.app.aftersale.api.AdminAftersaleCreateRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleListItemResponse;
import com.jzqs.app.aftersale.api.AdminAftersaleOrderOptionResponse;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveRequest;
import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.mobile.api.MobileAfterSaleItemResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AftersaleServiceImpl implements AftersaleService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public AftersaleServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AdminAftersaleListItemResponse> listCases(
        String status,
        String type,
        String startDate,
        String endDate,
        String view,
        Boolean hideAutoRefund
    ) {
        return jdbcTemplate.query(
            """
                SELECT
                    ac.id,
                    ac.meal_slot_order_id AS order_id,
                    ac.customer_id,
                    c.name AS customer_name,
                    c.phone AS customer_phone,
                    ord.serve_date AS serve_date,
                    mso.meal_period,
                    mso.status AS order_status,
                    ac.issue_type,
                    ac.status,
                    ac.source,
                    ac.source_category,
                    COALESCE(ac.reason_code, '') AS reason_code,
                    COALESCE(ac.issue_desc, '') AS reason_text,
                    COALESCE(ac.issue_param_summary, '') AS issue_param_summary,
                    ac.estimated_loss_meals,
                    ac.settled_loss_meals,
                    ac.wallet_delta,
                    ac.gift_zero_meal_count,
                    ac.gift_veggie_juice_count,
                    COALESCE(ac.resolution_action, '') AS resolution_action,
                    ac.refund_blocking,
                    COALESCE(ac.admin_remark, '') AS admin_remark,
                    ac.requested_at,
                    ac.processed_at
                FROM aftersale_cases ac
                JOIN customers c ON c.id = ac.customer_id
                JOIN meal_slot_orders mso ON mso.id = ac.meal_slot_order_id
                JOIN daily_orders ord ON ord.id = mso.daily_order_id
                WHERE (? IS NULL OR ? = '' OR ac.status = ?)
                  AND (? IS NULL OR ? = '' OR ac.issue_type = ?)
                  AND (? IS NULL OR ? = '' OR CAST(ord.serve_date AS CHAR) >= ?)
                  AND (? IS NULL OR ? = '' OR CAST(ord.serve_date AS CHAR) <= ?)
                  AND (
                      ? IS NULL OR ? = '' OR ? = 'ledger'
                      OR (? = 'settlement' AND ac.status IN ('PENDING', 'PROCESSING'))
                  )
                  AND (? IS NULL OR ? = FALSE OR ac.source_category <> 'AUTO_REFUND')
                ORDER BY ac.requested_at DESC, ac.id DESC
                """,
            (rs, rowNum) -> new AdminAftersaleListItemResponse(
                rs.getLong("id"),
                rs.getLong("order_id"),
                rs.getLong("customer_id"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                String.valueOf(rs.getObject("serve_date")),
                rs.getString("meal_period"),
                rs.getString("order_status"),
                rs.getString("issue_type"),
                rs.getString("status"),
                rs.getString("source"),
                rs.getString("source_category"),
                rs.getString("reason_code"),
                rs.getString("reason_text"),
                rs.getString("issue_param_summary"),
                rs.getInt("estimated_loss_meals"),
                rs.getInt("settled_loss_meals"),
                rs.getInt("wallet_delta"),
                rs.getInt("gift_zero_meal_count"),
                rs.getInt("gift_veggie_juice_count"),
                rs.getString("resolution_action"),
                rs.getBoolean("refund_blocking"),
                rs.getString("admin_remark"),
                formatTimestamp(rs.getTimestamp("requested_at")),
                formatNullableTimestamp(rs.getTimestamp("processed_at"))
            ),
            status, status, status,
            type, type, type,
            startDate, startDate, startDate,
            endDate, endDate, endDate,
            view, view, view, view,
            hideAutoRefund, hideAutoRefund
        );
    }

    @Override
    public List<AdminAftersaleOrderOptionResponse> orderOptions(String serveDate) {
        return jdbcTemplate.query(
            """
                SELECT
                    mso.id,
                    c.name AS customer_name,
                    c.phone AS customer_phone,
                    ord.serve_date,
                    mso.meal_period,
                    mso.status AS order_status,
                    COALESCE(da.detail_address, '') AS address_summary
                FROM meal_slot_orders mso
                JOIN daily_orders ord ON ord.id = mso.daily_order_id
                JOIN customers c ON c.id = ord.customer_id
                LEFT JOIN delivery_addresses da ON da.id = ord.delivery_address_id
                WHERE CAST(ord.serve_date AS CHAR) = ?
                ORDER BY c.name, mso.id DESC
                """,
            (rs, rowNum) -> new AdminAftersaleOrderOptionResponse(
                rs.getLong("id"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                String.valueOf(rs.getObject("serve_date")),
                rs.getString("meal_period"),
                rs.getString("order_status"),
                rs.getString("address_summary")
            ),
            serveDate
        );
    }

    @Override
    @Transactional
    public Map<String, Object> createCase(AdminAftersaleCreateRequest request) {
        OrderContext order = requireOrder(request.orderId(), null);
        ensureOrderAllowsAftersale(order.status());
        ensureNoOpenCase(request.orderId());
        String operatorName = fallbackOperator(request.operatorName(), "后台客服");
        long caseId = insertCase(
            request.orderId(),
            order.customerId(),
            request.type(),
            request.reasonCode(),
            request.reasonText(),
            normalizeText(request.issueParamSummary()),
            Math.max(request.estimatedLossMeals(), 0),
            normalizeSourceCategory(request.sourceCategory()),
            null,
            normalizeText(request.remark()),
            "ADMIN_DIRECT",
            "REFUND".equals(request.type()),
            operatorName
        );
        insertAction(caseId, "CREATE", request.reasonText(), operatorName);
        return Map.of("afterSaleId", caseId, "status", "PENDING");
    }

    @Override
    @Transactional
    public Map<String, Object> createMobileCase(long customerId, long orderId, MobileCreateAfterSaleRequest request) {
        OrderContext order = requireOrder(orderId, customerId);
        ensureOrderAllowsAftersale(order.status());
        ensureNoOpenCase(orderId);
        String operatorName = "小程序用户";
        boolean autoRefund = shouldAutoCompleteRefund(request.type(), order.status(), order.serveDate());
        long caseId = insertCase(
            orderId,
            customerId,
            request.type(),
            request.reasonCode(),
            request.reasonText(),
            "",
            0,
            autoRefund ? "AUTO_REFUND" : "NORMAL",
            normalizeText(request.remark()),
            "",
            "USER_APPLY",
            "REFUND".equals(request.type()),
            operatorName
        );
        insertAction(caseId, "CREATE", request.reasonText(), operatorName);
        if (autoRefund) {
            completeRefundCase(
                caseId,
                customerId,
                orderId,
                order.status(),
                request.reasonCode(),
                request.reasonText(),
                "未送达自动退款",
                "系统自动处理",
                1,
                "AUTO_REFUND_TO_WALLET"
            );
            insertAction(caseId, "AUTO_REFUND_TO_WALLET", "未送达自动退款", "系统自动处理");
            return Map.of("afterSaleId", caseId, "status", "COMPLETED");
        }
        return Map.of("afterSaleId", caseId, "status", "PENDING");
    }

    @Override
    @Transactional
    public Map<String, Object> resolveCase(long caseId, AdminAftersaleResolveRequest request) {
        ExistingCase existingCase = requireCase(caseId);
        String operatorName = fallbackOperator(request.operatorName(), "后台客服");
        String action = request.resolutionAction();
        int walletDelta = request.walletDelta() > 0 ? request.walletDelta() : 1;

        if ("COMPLETED".equals(existingCase.status()) || "REJECTED".equals(existingCase.status())) {
            return Map.of("caseId", caseId, "status", existingCase.status());
        }

        if ("REJECT".equals(action)) {
            jdbcTemplate.update(
                """
                    UPDATE aftersale_cases
                    SET resolution_type = ?,
                        resolution_action = ?,
                        settled_loss_meals = 0,
                        gift_zero_meal_count = 0,
                        gift_veggie_juice_count = 0,
                        status = 'REJECTED',
                        admin_remark = ?,
                        refund_blocking = FALSE,
                        processed_at = CURRENT_TIMESTAMP,
                        processed_by = ?,
                        operator_name = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                action,
                action,
                normalizeText(request.adminRemark()),
                operatorName,
                operatorName,
                caseId
            );
            insertAction(caseId, "REJECT", request.adminRemark(), operatorName);
            return Map.of("caseId", caseId, "status", "REJECTED");
        }

        if ("REFUND_TO_WALLET".equals(action)) {
            completeRefundCase(
                caseId,
                existingCase.customerId(),
                existingCase.orderId(),
                existingCase.orderStatus(),
                existingCase.reasonCode(),
                existingCase.reasonText(),
                normalizeText(request.adminRemark()),
                operatorName,
                walletDelta,
                action
            );
            insertAction(caseId, "REFUND_TO_WALLET", request.adminRemark(), operatorName);
            return Map.of("caseId", caseId, "status", "COMPLETED");
        }

        if (request.walletDelta() > 0 && !walletTransactionExists(caseId, "COMPENSATION_RETURN")) {
            long walletId = findActiveWalletId(existingCase.customerId());
            jdbcTemplate.update(
                "UPDATE meal_wallets SET total_meals = total_meals + ? WHERE id = ?",
                request.walletDelta(),
                walletId
            );
            insertWalletTransaction(
                walletId,
                "COMPENSATION_RETURN",
                request.walletDelta(),
                operatorName,
                "售后补回餐次",
                caseId,
                existingCase.orderId(),
                null,
                null,
                null,
                LocalDateTime.now()
            );
        }
        jdbcTemplate.update(
            """
                UPDATE aftersale_cases
                SET resolution_type = ?,
                    resolution_action = ?,
                    wallet_delta = ?,
                    settled_loss_meals = ?,
                    gift_zero_meal_count = ?,
                    gift_veggie_juice_count = ?,
                    refund_blocking = ?,
                    status = 'COMPLETED',
                    admin_remark = ?,
                    processed_at = CURRENT_TIMESTAMP,
                    processed_by = ?,
                    operator_name = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            action,
            action,
            Math.max(request.walletDelta(), 0),
            Math.max(request.settledLossMeals(), 0),
            Math.max(request.giftZeroMealCount(), 0),
            Math.max(request.giftVeggieJuiceCount(), 0),
            request.refundBlocking(),
            normalizeText(request.adminRemark()),
            operatorName,
            operatorName,
            caseId
        );
        insertAction(caseId, action, request.adminRemark(), operatorName);
        return Map.of("caseId", caseId, "status", "COMPLETED");
    }

    @Override
    public List<MobileAfterSaleItemResponse> customerCases(long customerId) {
        return jdbcTemplate.query(
            """
                SELECT
                    id,
                    meal_slot_order_id,
                    issue_type,
                    status,
                    COALESCE(reason_code, '') AS reason_code,
                    COALESCE(issue_desc, '') AS reason_text,
                    COALESCE(admin_remark, '') AS admin_remark,
                    requested_at,
                    processed_at
                FROM aftersale_cases
                WHERE customer_id = ?
                ORDER BY requested_at DESC, id DESC
                """,
            (rs, rowNum) -> new MobileAfterSaleItemResponse(
                rs.getLong("id"),
                rs.getLong("meal_slot_order_id"),
                rs.getString("issue_type"),
                rs.getString("status"),
                rs.getString("reason_code"),
                rs.getString("reason_text"),
                rs.getString("admin_remark"),
                formatTimestamp(rs.getTimestamp("requested_at")),
                formatNullableTimestamp(rs.getTimestamp("processed_at"))
            ),
            customerId
        );
    }

    private OrderContext requireOrder(long orderId, Long expectedCustomerId) {
        List<OrderContext> rows = jdbcTemplate.query(
            """
                SELECT
                    mso.id,
                    do.customer_id,
                    mso.status,
                    do.serve_date
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE mso.id = ?
                """,
            (rs, rowNum) -> new OrderContext(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("status"),
                rs.getObject("serve_date", LocalDate.class)
            ),
            orderId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单不存在");
        }
        OrderContext order = rows.get(0);
        if (expectedCustomerId != null && order.customerId() != expectedCustomerId.longValue()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单不存在");
        }
        return order;
    }

    private ExistingCase requireCase(long caseId) {
        List<ExistingCase> rows = jdbcTemplate.query(
            """
                SELECT
                    ac.id,
                    ac.customer_id,
                    ac.meal_slot_order_id,
                    ac.status,
                    COALESCE(ac.reason_code, '') AS reason_code,
                    COALESCE(ac.issue_desc, '') AS reason_text,
                    mso.status AS order_status
                FROM aftersale_cases ac
                JOIN meal_slot_orders mso ON mso.id = ac.meal_slot_order_id
                WHERE ac.id = ?
                """,
            (rs, rowNum) -> new ExistingCase(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getLong("meal_slot_order_id"),
                rs.getString("status"),
                rs.getString("reason_code"),
                rs.getString("reason_text"),
                rs.getString("order_status")
            ),
            caseId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.AFTERSALE_NOT_FOUND, "售后记录不存在");
        }
        return rows.get(0);
    }

    private void ensureOrderAllowsAftersale(String orderStatus) {
        if ("CANCELLED".equals(orderStatus) || "REFUNDED".equals(orderStatus)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "当前订单不可发起售后");
        }
    }

    private void ensureNoOpenCase(long orderId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM aftersale_cases
                WHERE meal_slot_order_id = ?
                  AND status IN ('PENDING', 'PROCESSING', 'APPROVED')
                """,
            Integer.class,
            orderId
        );
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "该订单已有处理中售后");
        }
    }

    private long insertCase(
        long orderId,
        long customerId,
        String type,
        String reasonCode,
        String reasonText,
        String issueParamSummary,
        int estimatedLossMeals,
        String sourceCategory,
        String userRemark,
        String adminRemark,
        String source,
        boolean refundBlocking,
        String operatorName
    ) {
        LocalDateTime now = LocalDateTime.now();
        return insertAndReturnId(
            """
                INSERT INTO aftersale_cases (
                    meal_slot_order_id,
                    customer_id,
                    issue_type,
                    issue_desc,
                    resolution_type,
                    rollback_meal,
                    bonus_meals,
                    compensation_item,
                    status,
                    operator_name,
                    source,
                    source_category,
                    reason_code,
                    issue_param_summary,
                    estimated_loss_meals,
                    user_remark,
                    admin_remark,
                    resolution_action,
                    wallet_delta,
                    refund_blocking,
                    requested_at,
                    processed_at,
                    processed_by,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            orderId,
            customerId,
            type,
            normalizeText(reasonText),
            "REGISTER_ONLY",
            false,
            0,
            "",
            "PENDING",
            operatorName,
            source,
            sourceCategory,
            normalizeText(reasonCode),
            normalizeText(issueParamSummary),
            Math.max(estimatedLossMeals, 0),
            normalizeText(userRemark),
            normalizeText(adminRemark),
            null,
            0,
            refundBlocking,
            Timestamp.valueOf(now),
            null,
            null,
            Timestamp.valueOf(now),
            Timestamp.valueOf(now)
        );
    }

    private void clearDispatchLinks(long orderId) {
        List<Long> batchIds = jdbcTemplate.query(
            "SELECT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
            (rs, rowNum) -> rs.getLong("batch_id"),
            orderId
        );
        jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE meal_slot_order_id = ?", orderId);
        for (Long batchId : batchIds) {
            jdbcTemplate.update(
                """
                    UPDATE dispatch_batches
                    SET total_count = (SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ?),
                        delivered_count = (SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ? AND item_status = 'DELIVERED')
                    WHERE id = ?
                    """,
                batchId,
                batchId,
                batchId
            );
        }
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE meal_slot_order_id = ?", orderId);
    }

    private long findActiveWalletId(long customerId) {
        Long walletId = jdbcTemplate.query(
            "SELECT id FROM meal_wallets WHERE customer_id = ? AND active = TRUE",
            ps -> ps.setLong(1, customerId),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (walletId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户未开通有效餐次账户");
        }
        return walletId;
    }

    private void rollbackMeal(long walletId, String orderStatus) {
        if ("DELIVERED".equals(orderStatus)) {
            jdbcTemplate.update(
                "UPDATE meal_wallets SET consumed_meals = CASE WHEN consumed_meals > 0 THEN consumed_meals - 1 ELSE 0 END WHERE id = ?",
                walletId
            );
            return;
        }
        jdbcTemplate.update(
            "UPDATE meal_wallets SET reserved_meals = CASE WHEN reserved_meals > 0 THEN reserved_meals - 1 ELSE 0 END WHERE id = ?",
            walletId
        );
    }

    private boolean walletTransactionExists(long aftersaleId, String transactionType) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wallet_transactions WHERE related_aftersale_id = ? AND transaction_type = ?",
            Integer.class,
            aftersaleId,
            transactionType
        );
        return count != null && count > 0;
    }

    private boolean shouldAutoCompleteRefund(String type, String orderStatus, LocalDate serveDate) {
        // 秒退逻辑：仅限“前一天”申请“明日待配送”订单的退款
        // 如果已经是配送当天，则必须进入人工审核流程
        LocalDate today = LocalDate.now();
        return "REFUND".equals(type) 
            && "PENDING_DISPATCH".equals(orderStatus) 
            && serveDate.isAfter(today);
    }

    private void completeRefundCase(
        long caseId,
        long customerId,
        long orderId,
        String orderStatus,
        String reasonCode,
        String reasonText,
        String adminRemark,
        String operatorName,
        int walletDelta,
        String action
    ) {
        clearDispatchLinks(orderId);
        if (!walletTransactionExists(caseId, "REFUND_RETURN")) {
            long walletId = findActiveWalletId(customerId);
            rollbackMeal(walletId, orderStatus);
            Long originalTransactionId = findOriginalTransactionId(walletId, orderId, orderStatus);
            
            // 自动计算退回数量：如果找到原扣餐记录，则按原扣餐额度退回（取绝对值），忽略前端传入的数量
            int effectiveDelta = walletDelta;
            if (originalTransactionId != null) {
                Integer originalDelta = jdbcTemplate.queryForObject(
                    "SELECT meal_delta FROM wallet_transactions WHERE id = ?",
                    Integer.class,
                    originalTransactionId
                );
                if (originalDelta != null) {
                    effectiveDelta = Math.abs(originalDelta);
                }
            }

            long refundTransactionId = insertWalletTransaction(
                walletId,
                "REFUND_RETURN",
                effectiveDelta,
                operatorName,
                buildRefundRemark(reasonText),
                caseId,
                orderId,
                originalTransactionId,
                reasonCode,
                reasonText,
                LocalDateTime.now()
            );
            if (originalTransactionId != null) {
                markOriginalTransactionRefunded(
                    originalTransactionId,
                    refundTransactionId,
                    caseId,
                    reasonCode,
                    reasonText
                );
            }
            
            // 更新售后单状态，并确保 wallet_delta 记录的是实际退回的数量
            jdbcTemplate.update(
                """
                    UPDATE aftersale_cases
                    SET resolution_type = ?,
                        resolution_action = ?,
                        rollback_meal = TRUE,
                        wallet_delta = ?,
                        settled_loss_meals = ?,
                        gift_zero_meal_count = 0,
                        gift_veggie_juice_count = 0,
                        refund_blocking = TRUE,
                        status = 'COMPLETED',
                        admin_remark = ?,
                        processed_at = CURRENT_TIMESTAMP,
                        processed_by = ?,
                        operator_name = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                action,
                action,
                effectiveDelta,
                effectiveDelta,
                normalizeText(adminRemark),
                operatorName,
                operatorName,
                caseId
            );
        }
        jdbcTemplate.update(
            "UPDATE meal_slot_orders SET status = 'REFUNDED' WHERE id = ? AND status <> 'REFUNDED'",
            orderId
        );
    }

    private void insertAction(long caseId, String actionType, String actionNote, String operatorName) {
        jdbcTemplate.update(
            "INSERT INTO aftersale_actions (aftersale_case_id, action_type, action_note, operator_name) VALUES (?, ?, ?, ?)",
            caseId,
            actionType,
            normalizeText(actionNote),
            operatorName
        );
    }

    private Long findOriginalTransactionId(long walletId, long orderId, String orderStatus) {
        String originalType = "DELIVERED".equals(orderStatus) ? "CONSUME" : "RESERVE";
        return jdbcTemplate.query(
            """
                SELECT id
                FROM wallet_transactions
                WHERE wallet_id = ?
                  AND transaction_type = ?
                  AND refunded = FALSE
                  AND (related_order_id = ? OR related_order_id IS NULL)
                ORDER BY CASE WHEN related_order_id = ? THEN 0 ELSE 1 END, id DESC
                LIMIT 1
                """,
            ps -> {
                ps.setLong(1, walletId);
                ps.setString(2, originalType);
                ps.setLong(3, orderId);
                ps.setLong(4, orderId);
            },
            rs -> rs.next() ? rs.getLong("id") : null
        );
    }

    private void markOriginalTransactionRefunded(
        long originalTransactionId,
        long refundTransactionId,
        long aftersaleId,
        String reasonCode,
        String reasonText
    ) {
        jdbcTemplate.update(
            """
                UPDATE wallet_transactions
                SET refunded = TRUE,
                    related_aftersale_id = ?,
                    related_transaction_id = ?,
                    refund_reason_code = ?,
                    refund_reason_text = ?
                WHERE id = ?
                """,
            aftersaleId,
            refundTransactionId,
            normalizeText(reasonCode),
            normalizeText(reasonText),
            originalTransactionId
        );
    }

    private long insertWalletTransaction(
        long walletId,
        String transactionType,
        int mealDelta,
        String operatorName,
        String remark,
        long aftersaleId,
        long orderId,
        Long relatedTransactionId,
        String refundReasonCode,
        String refundReasonText,
        LocalDateTime createdAt
    ) {
        return insertAndReturnId(
            """
                INSERT INTO wallet_transactions (
                    wallet_id, transaction_type, meal_delta, operator_name, remark,
                    related_order_id, related_aftersale_id, related_transaction_id,
                    snapshot_balance, refunded, refund_reason_code, refund_reason_text, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?, ?)
                """,
            walletId,
            transactionType,
            mealDelta,
            operatorName,
            remark,
            orderId,
            aftersaleId,
            relatedTransactionId,
            querySnapshotBalance(walletId),
            normalizeText(refundReasonCode),
            normalizeText(refundReasonText),
            Timestamp.valueOf(createdAt)
        );
    }

    private String buildRefundRemark(String reasonText) {
        String normalizedReason = normalizeText(reasonText);
        if (normalizedReason.isEmpty()) {
            return "已退款退回餐次";
        }
        return "已退款退回餐次：" + normalizedReason;
    }

    private int querySnapshotBalance(long walletId) {
        Integer balance = jdbcTemplate.queryForObject(
            "SELECT total_meals - reserved_meals - consumed_meals FROM meal_wallets WHERE id = ?",
            Integer.class,
            walletId
        );
        return balance == null ? 0 : balance;
    }

    private String fallbackOperator(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeSourceCategory(String value) {
        if ("AUTO_REFUND".equalsIgnoreCase(normalizeText(value))) {
            return "AUTO_REFUND";
        }
        return "NORMAL";
    }

    private String formatTimestamp(Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String formatNullableTimestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().format(DATE_TIME_FORMATTER);
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

    private record OrderContext(long orderId, long customerId, String status, LocalDate serveDate) {
    }

    private record ExistingCase(
        long caseId,
        long customerId,
        long orderId,
        String status,
        String reasonCode,
        String reasonText,
        String orderStatus
    ) {
    }
}
