package com.jzqs.app.dispatch.service.impl;

import com.jzqs.app.admin.persistence.AdminRowMappers;
import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeEvent;
import com.jzqs.app.common.realtime.TransactionalRealtimePublisher;
import com.jzqs.app.dispatch.api.DispatchAreaBindingRemoveResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingUpdateResultResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBlockingOrderResponse;
import com.jzqs.app.dispatch.api.DispatchAreaDeleteResponse;
import com.jzqs.app.dispatch.api.DispatchAreaOrderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchAutoAssignResponse;
import com.jzqs.app.dispatch.api.DispatchAreaOrderItemResponse;
import com.jzqs.app.dispatch.api.DispatchAreaOrdersReorderResponse;
import com.jzqs.app.dispatch.api.DispatchAreaRenameResponse;
import com.jzqs.app.dispatch.api.DispatchAreaRiderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchBatchResponse;
import com.jzqs.app.dispatch.api.DispatchBoardItemResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingResponse;
import com.jzqs.app.dispatch.api.DispatchExceptionAreaConfirmResponse;
import com.jzqs.app.dispatch.api.DispatchExceptionItemResponse;
import com.jzqs.app.dispatch.api.DispatchManagedRiderResponse;
import com.jzqs.app.dispatch.api.DispatchNotificationResponse;
import com.jzqs.app.dispatch.api.DispatchOverviewResponse;
import com.jzqs.app.dispatch.api.DispatchOrderAreaMoveResponse;
import com.jzqs.app.dispatch.api.DispatchOrderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchOrderReorderItemRequest;
import com.jzqs.app.dispatch.api.DispatchPendingItemResponse;
import com.jzqs.app.dispatch.api.DispatchReassignResultResponse;
import com.jzqs.app.dispatch.api.DispatchReassignmentResponse;
import com.jzqs.app.dispatch.api.DispatchRiderActivateResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthBindingResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthTakeoverResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthUnbindResponse;
import com.jzqs.app.dispatch.api.DispatchRiderProfileUpsertResponse;
import com.jzqs.app.dispatch.api.DispatchRiderProgressResponse;
import com.jzqs.app.dispatch.api.DispatchRiderStatusResponse;
import com.jzqs.app.dispatch.api.PendingRiderResponse;
import com.jzqs.app.dispatch.service.DispatchService;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
public class DispatchServiceImpl implements DispatchService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DISPATCH_EXCEPTION_RETENTION_DAYS = 30;
    private static final int DISPATCH_REASSIGNMENT_RETENTION_DAYS = 30;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionalRealtimePublisher realtimeEventPublisher;

    public DispatchServiceImpl(JdbcTemplate jdbcTemplate, TransactionalRealtimePublisher realtimeEventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    @Override
    public PageResponse<DispatchBoardItemResponse> board() {
        String sql = """
            SELECT
                da.id AS dispatch_id,
                mso.id AS order_id,
                c.name AS customer_name,
                ca.address_line AS delivery_address,
                da.rider_name,
                da.area_code,
                da.status AS delivery_status,
                CASE WHEN dr.id IS NULL THEN 'PENDING' ELSE 'UPLOADED' END AS receipt_status,
                CASE WHEN dr.id IS NULL THEN '绛夊緟涓婁紶' ELSE '[鏌ョ湅鍥炴墽鍥剧墖]' END AS receipt_label,
                CASE WHEN da.status = 'DELIVERED' THEN FALSE ELSE TRUE END AS can_notify_customer
            FROM dispatch_assignments da
            JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
            ORDER BY da.id
            """;
        List<DispatchBoardItemResponse> items = jdbcTemplate.query(sql, AdminRowMappers.DISPATCH_BOARD);
        return PageResponse.of(items, 1, 20, items.size());
    }

    @Override
    public DispatchOverviewResponse overview(String mealPeriod, String serveDate) {
        LocalDate targetDate = serveDate == null || serveDate.isEmpty() 
            ? LocalDate.now() 
            : LocalDate.parse(serveDate);
        
        autoAssignRememberedPendingOrders(mealPeriod);
        int pendingCount = queryCount(
            """
                SELECT COALESCE(SUM(mso.quantity), 0)
                FROM meal_slot_orders mso
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id = mso.id
                LEFT JOIN rider_address_bindings rab ON rab.customer_id = doo.customer_id AND rab.address_id = mso.address_id
                WHERE mso.status = 'PENDING_DISPATCH'
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                  AND da.id IS NULL
                  AND rab.id IS NULL
                """,
            targetDate,
            normalizedMealPeriod(mealPeriod),
            normalizedMealPeriod(mealPeriod)
        );
        int dispatchingCount = queryCount(
            """
                SELECT COALESCE(SUM(mso.quantity), 0)
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE da.status = 'DISPATCHING'
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                """,
            targetDate,
            normalizedMealPeriod(mealPeriod),
            normalizedMealPeriod(mealPeriod)
        );
        int missingRiderAreaCount = queryCount(
            """
                SELECT COUNT(DISTINCT da.area_code)
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE da.status = 'AREA_ASSIGNED'
                  AND da.rider_name IS NULL
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                """,
            targetDate,
            normalizedMealPeriod(mealPeriod),
            normalizedMealPeriod(mealPeriod)
        );
        return new DispatchOverviewResponse(
            pendingCount,
            dispatchingCount,
            missingRiderAreaCount
        );
    }

    @Override
    public List<DispatchBatchResponse> batches(String serveDate, String mealPeriod) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                db.id AS batch_id,
                db.serve_date AS serve_date,
                db.meal_period,
                db.rider_profile_id,
                rp.rider_name,
                db.area_code,
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
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        if (serveDate != null && !serveDate.isBlank()) {
            sql.append(" AND db.serve_date = ?");
            args.add(LocalDate.parse(serveDate));
        }
        if (mealPeriod != null && !mealPeriod.isBlank()) {
            sql.append(" AND db.meal_period = ?");
            args.add(mealPeriod);
        }
        sql.append(" ORDER BY db.serve_date DESC, db.meal_period, db.id");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DispatchBatchResponse(
            rs.getLong("batch_id"),
            rs.getDate("serve_date").toLocalDate().toString(),
            rs.getString("meal_period"),
            rs.getLong("rider_profile_id"),
            rs.getString("rider_name"),
            rs.getString("area_code"),
            rs.getString("batch_status"),
            rs.getInt("total_count"),
            rs.getInt("delivered_count"),
            rs.getInt("current_sequence"),
            rs.getString("current_customer_name"),
            rs.getString("next_customer_name")
        ), args.toArray());
    }

    @Override
    public List<DispatchRiderProgressResponse> riderProgress(String mealPeriod, String serveDate) {
        LocalDate targetDate = serveDate == null || serveDate.isEmpty()
            ? LocalDate.now()
            : LocalDate.parse(serveDate);
        String finalMealPeriod = normalizedMealPeriod(mealPeriod);
        List<RiderProgressRow> rows = jdbcTemplate.query(
            """
                SELECT
                    da.rider_name,
                    da.area_code,
                    da.meal_slot_order_id AS order_id,
                    COALESCE(NULLIF(da.sequence_number, 0), dbi.current_sequence, 0) AS sequence_number,
                    da.status AS delivery_status,
                    mso.quantity
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                LEFT JOIN dispatch_batch_items dbi ON dbi.meal_slot_order_id = da.meal_slot_order_id
                WHERE doo.serve_date = ?
                  AND da.rider_name IS NOT NULL
                  AND da.rider_name <> ''
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                ORDER BY da.rider_name, da.area_code, da.sequence_number, da.id
                """,
            (rs, rowNum) -> new RiderProgressRow(
                rs.getString("rider_name"),
                rs.getString("area_code"),
                rs.getLong("order_id"),
                rs.getInt("sequence_number"),
                rs.getString("delivery_status"),
                rs.getInt("quantity")
            ),
            targetDate,
            finalMealPeriod,
            finalMealPeriod
        );
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> exceptionCounts = loadRiderExceptionCounts(targetDate, finalMealPeriod);
        List<DispatchRiderProgressResponse> responses = new ArrayList<>();
        RiderProgressAccumulator current = null;
        for (RiderProgressRow row : rows) {
            String key = riderProgressKey(row.riderName(), row.areaCode());
            if (current == null || !current.matches(key)) {
                if (current != null) {
                    responses.add(current.toResponse(exceptionCounts.getOrDefault(current.key(), 0)));
                }
                current = new RiderProgressAccumulator(key, row.riderName(), row.areaCode());
            }
            current.add(row);
        }
        if (current != null) {
            responses.add(current.toResponse(exceptionCounts.getOrDefault(current.key(), 0)));
        }
        return responses;
    }

    @Override
    public List<DispatchExceptionItemResponse> exceptions() {
        String sql = """
            SELECT
                mso.id AS meal_slot_order_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                ca.address_line AS delivery_address,
                COALESCE(rab.area_code, ca.area_code) AS suggested_area_code,
                rp_default.rider_name AS suggested_rider_name,
                CASE
                    WHEN rab.id IS NULL THEN 'NEW_ADDRESS'
                    WHEN dab.id IS NULL THEN 'AREA_RULE_MISSING'
                    WHEN rp_default.id IS NULL THEN 'RIDER_UNAVAILABLE'
                    ELSE 'CONFLICT_REQUIRES_CONFIRM'
                END AS exception_type,
                CASE
                    WHEN rab.id IS NULL THEN '鏂板湴鍧€锛屽皻鏈‘璁ゅ尯鍩?
                    WHEN dab.id IS NULL THEN '璇ュ尯鍩熸湭璁剧疆榛樿楠戞墜'
                    WHEN rp_default.id IS NULL THEN '榛樿楠戞墜褰撳墠涓嶅彲娲惧崟'
                    ELSE '璇ュ湴鍧€闇€瑕侀噸鏂扮‘璁ゅ尯鍩熷綊灞?
                END AS reason,
                CASE WHEN rab.id IS NULL THEN FALSE ELSE TRUE END AS remembered_address
            FROM meal_slot_orders mso
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            JOIN customers c ON c.id = doo.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN rider_address_bindings rab ON rab.customer_id = doo.customer_id AND rab.address_id = mso.address_id
            LEFT JOIN dispatch_area_bindings dab ON dab.area_code = rab.area_code
            LEFT JOIN rider_profiles rp_default
                ON rp_default.id = dab.default_rider_profile_id
               AND rp_default.auth_status = 'ACTIVE'
               AND rp_default.employment_status = 'ACTIVE'
            LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id = mso.id
            WHERE mso.status = 'PENDING_DISPATCH' AND da.id IS NULL
            ORDER BY mso.id
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DispatchExceptionItemResponse(
            rs.getLong("meal_slot_order_id"),
            rs.getString("exception_type"),
            rs.getString("reason"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("delivery_address"),
            rs.getString("suggested_area_code"),
            rs.getString("suggested_rider_name"),
            rs.getBoolean("remembered_address")
        ));
    }

    @Override
    public List<DispatchPendingItemResponse> pendingItems(String mealPeriod, String serveDate) {
        LocalDate targetDate = serveDate == null || serveDate.isEmpty() 
            ? LocalDate.now() 
            : LocalDate.parse(serveDate);
        
        autoAssignRememberedPendingOrders(mealPeriod);
        String sql = """
            SELECT
                mso.id AS order_id,
                c.name AS customer_name,
                ca.address_line AS delivery_address
            FROM meal_slot_orders mso
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            JOIN customers c ON c.id = doo.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN rider_address_bindings rab ON rab.customer_id = doo.customer_id AND rab.address_id = mso.address_id
            LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id = mso.id
            WHERE mso.status = 'PENDING_DISPATCH'
              AND doo.serve_date = ?
              AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
              AND da.id IS NULL
              AND rab.id IS NULL
            ORDER BY mso.id
            """;

        String finalMealPeriod = normalizedMealPeriod(mealPeriod);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DispatchPendingItemResponse(
            rs.getLong("order_id"),
            rs.getString("customer_name"),
            rs.getString("delivery_address")
        ), targetDate, finalMealPeriod, finalMealPeriod);
    }

    @Override
    @Transactional
    public DispatchAutoAssignResponse autoAssignPendingOrders() {
        return autoAssignPendingOrders(null);
    }

    @Override
    @Transactional
    public DispatchAutoAssignResponse autoAssignPendingOrders(String mealPeriod) {
        int assignedCount = autoAssignRememberedPendingOrders(mealPeriod);
        return new DispatchAutoAssignResponse(assignedCount, 0);
    }

    @Override
    @Transactional
    public BatchOperationResponse batchAssignPendingOrders(
        List<Long> orderIds,
        String areaCode,
        String updatedBy
    ) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        String defaultRiderName = resolveDefaultRiderName(normalizedAreaCode);
        int successCount = 0;
        List<BatchOperationResponse.FailureItem> failures = new ArrayList<>();
        for (Long orderId : orderIds) {
            try {
                if (orderId == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "璁㈠崟缂栧彿涓嶈兘涓虹┖");
                }
                if (defaultRiderName != null) {
                    dispatchOrder(orderId, defaultRiderName, normalizedAreaCode, true);
                } else {
                    DispatchOrderContext orderContext = loadOrderContext(orderId);
                    int sequenceNumber = nextAreaSequence(
                        normalizedAreaCode,
                        orderContext.serveDate(),
                        orderContext.mealPeriod()
                    );
                    Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = ?", Integer.class, orderId);
                    if (existing != null && existing > 0) {
                        jdbcTemplate.update(
                            """
                                UPDATE dispatch_assignments
                                SET rider_name = NULL,
                                    rider_profile_id = NULL,
                                    area_code = ?,
                                    status = 'AREA_ASSIGNED',
                                    sequence_number = ?
                                WHERE meal_slot_order_id = ?
                                """,
                            normalizedAreaCode,
                            sequenceNumber,
                            orderId
                        );
                    } else {
                        insertAndReturnId(
                            """
                                INSERT INTO dispatch_assignments (
                                    meal_slot_order_id,
                                    rider_name,
                                    rider_profile_id,
                                    area_code,
                                    status,
                                    sequence_number,
                                    created_at
                                ) VALUES (?, NULL, NULL, ?, ?, ?, CURRENT_TIMESTAMP)
                                """,
                            orderId,
                            normalizedAreaCode,
                            "AREA_ASSIGNED",
                            sequenceNumber
                        );
                    }
                    syncAddressBindingForArea(orderId, normalizedAreaCode, updatedBy, "AREA_CONFIRMED");
                }
                successCount++;
            } catch (RuntimeException ex) {
                failures.add(new BatchOperationResponse.FailureItem(
                    orderId,
                    "BATCH_ASSIGN_FAILED",
                    ex.getMessage() == null ? "鎵归噺澶勭悊澶辫触" : ex.getMessage()
                ));
            }
        }
        publishDispatchEvent("dispatch.assignment.changed", normalizedAreaCode, defaultRiderName, successCount);
        return new BatchOperationResponse(successCount, failures.size(), failures);
    }

    @Override
    @Transactional
    public DispatchNotificationResponse notifyCustomer(long dispatchId) {
        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispatch_assignments WHERE id = ?", Integer.class, dispatchId);
        if (exists == null || exists == 0) {
            return new DispatchNotificationResponse(dispatchId, "NOT_FOUND");
        }
        loadDispatchNotificationContext(dispatchId);
        return new DispatchNotificationResponse(dispatchId, "SKIPPED");
    }

    @Override
    @Transactional
    public DispatchOrderAssignResponse assignOrder(long orderId, String riderName, String areaCode) {
        dispatchOrder(orderId, riderName, areaCode, true);
        markDispatchExceptionResolved(orderId, riderName);
        publishDispatchEvent("dispatch.assignment.changed", areaCode, riderName, orderId);
        return new DispatchOrderAssignResponse(orderId, riderName, "DISPATCHED");
    }

    @Override
    @Transactional
    public DispatchExceptionAreaConfirmResponse confirmExceptionArea(
        long mealSlotOrderId,
        String areaCode,
        String riderName,
        boolean rememberAddress,
        String updatedBy
    ) {
        dispatchOrder(mealSlotOrderId, riderName, areaCode, rememberAddress);
        markDispatchExceptionResolved(mealSlotOrderId, riderName);
        publishDispatchEvent("dispatch.assignment.changed", areaCode, riderName, mealSlotOrderId);
        return new DispatchExceptionAreaConfirmResponse(
            mealSlotOrderId,
            areaCode,
            riderName,
            rememberAddress,
            updatedBy,
            "CONFIRMED"
        );
    }

    private void dispatchOrder(long orderId, String riderName, String areaCode, boolean syncAddressBinding) {
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'DISPATCHING' WHERE id = ?", orderId);
        long riderProfileId = ensureRiderProfile(riderName, areaCode);
        DispatchOrderContext orderContext = loadOrderContext(orderId);
        int sequenceNumber = nextAreaSequence(
            areaCode,
            orderContext.serveDate(),
            orderContext.mealPeriod()
        );
        Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = ?", Integer.class, orderId);
        if (existing != null && existing > 0) {
            jdbcTemplate.update(
                """
                    UPDATE dispatch_assignments
                    SET rider_name = ?,
                        rider_profile_id = ?,
                        area_code = ?,
                        status = 'DISPATCHING',
                        sequence_number = CASE WHEN sequence_number = 0 THEN ? ELSE sequence_number END
                    WHERE meal_slot_order_id = ?
                    """,
                riderName, riderProfileId, areaCode, sequenceNumber, orderId
            );
        } else {
            insertAndReturnId(
                """
                    INSERT INTO dispatch_assignments (
                        meal_slot_order_id,
                        rider_name,
                        rider_profile_id,
                        area_code,
                        status,
                        sequence_number
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                orderId, riderName, riderProfileId, areaCode, "DISPATCHING", sequenceNumber
            );
        }
        if (syncAddressBinding) {
            syncAddressBinding(orderId, riderProfileId, areaCode);
        }
        int finalSequenceNumber = ensureBatchItem(orderId, riderProfileId, areaCode);
        jdbcTemplate.update(
            "UPDATE dispatch_assignments SET sequence_number = ? WHERE meal_slot_order_id = ?",
            finalSequenceNumber,
            orderId
        );
    }

    @Override
    public List<PendingRiderResponse> pendingRiders() {
        return jdbcTemplate.query(
            """
                SELECT
                    id,
                    COALESCE(display_name, rider_name) AS display_name,
                    phone,
                    current_openid,
                    auth_status,
                    first_login_at,
                    last_login_at
                FROM rider_profiles
                WHERE auth_status = 'UNASSIGNED'
                ORDER BY CASE WHEN last_login_at IS NULL THEN 1 ELSE 0 END, last_login_at DESC, id DESC
                """,
            (rs, rowNum) -> new PendingRiderResponse(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getString("phone"),
                rs.getString("current_openid"),
                rs.getString("auth_status"),
                formatTimestamp(rs.getTimestamp("first_login_at")),
                formatTimestamp(rs.getTimestamp("last_login_at"))
            )
        );
    }

    @Override
    public List<DispatchManagedRiderResponse> managedRiders(String authStatus, String keyword, String areaCode) {
        StringBuilder sql = new StringBuilder(
            """
                SELECT
                    rp.id,
                    rp.rider_name,
                    COALESCE(rp.display_name, rp.rider_name) AS display_name,
                    rp.phone,
                    rp.auth_status,
                    rp.employment_status,
                    rp.default_area_code,
                    rp.assigned_by,
                    rp.current_openid,
                    rp.first_login_at,
                    rp.last_login_at,
                    COALESCE((
                        SELECT SUM(db.total_count)
                        FROM dispatch_batches db
                        WHERE db.rider_profile_id = rp.id
                          AND db.serve_date = CURRENT_DATE
                    ), 0) AS today_task_count,
                    COALESCE((
                        SELECT SUM(db.delivered_count)
                        FROM dispatch_batches db
                        WHERE db.rider_profile_id = rp.id
                          AND db.serve_date = CURRENT_DATE
                    ), 0) AS today_delivered_count
                FROM rider_profiles rp
                WHERE 1 = 1
                """
        );
        List<Object> args = new ArrayList<>();
        if (authStatus != null && !authStatus.isBlank()) {
            sql.append(" AND rp.auth_status = ?");
            args.add(authStatus);
        }
        if (areaCode != null && !areaCode.isBlank()) {
            sql.append(" AND rp.default_area_code = ?");
            args.add(areaCode);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (rp.rider_name LIKE ? OR COALESCE(rp.display_name, rp.rider_name) LIKE ? OR COALESCE(rp.phone, '') LIKE ?)");
            String likeKeyword = "%" + keyword.trim() + "%";
            args.add(likeKeyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
        }
        sql.append(" ORDER BY CASE rp.auth_status WHEN 'ACTIVE' THEN 0 WHEN 'UNASSIGNED' THEN 1 ELSE 2 END, rp.id DESC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DispatchManagedRiderResponse(
            rs.getLong("id"),
            rs.getString("rider_name"),
            rs.getString("display_name"),
            rs.getString("phone"),
            rs.getString("auth_status"),
            rs.getString("employment_status"),
            rs.getString("default_area_code"),
            rs.getString("assigned_by"),
            formatTimestamp(rs.getTimestamp("first_login_at")),
            formatTimestamp(rs.getTimestamp("last_login_at")),
            rs.getInt("today_task_count"),
            rs.getInt("today_delivered_count"),
            rs.getString("current_openid")
        ), args.toArray());
    }

    @Override
    @Transactional
    public DispatchRiderProfileUpsertResponse createRider(
        String riderName,
        String displayName,
        String phone,
        String areaCode,
        String employmentStatus,
        String updatedBy
    ) {
        String normalizedAreaCode = areaCode == null || areaCode.isBlank() ? null : areaCode.trim();
        boolean active = "ACTIVE".equalsIgnoreCase(employmentStatus);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        long riderId = insertAndReturnId(
            """
                INSERT INTO rider_profiles (
                    rider_name,
                    display_name,
                    phone,
                    employment_status,
                    default_area_code,
                    display_order,
                    remark,
                    auth_status,
                    assigned_at,
                    assigned_by,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            riderName,
            displayName,
            phone,
            employmentStatus,
            normalizedAreaCode,
            0,
            null,
            active ? "ACTIVE" : "DISABLED",
            Timestamp.valueOf(now),
            updatedBy,
            Timestamp.valueOf(now)
        );
        if (normalizedAreaCode != null && active) {
            updateAreaBinding(normalizedAreaCode, null, riderId, null, updatedBy);
        }
        return new DispatchRiderProfileUpsertResponse(
            riderId,
            riderName,
            displayName,
            phone,
            normalizedAreaCode,
            active ? "ACTIVE" : "DISABLED"
        );
    }

    @Override
    @Transactional
    public DispatchRiderProfileUpsertResponse updateRiderProfile(
        long riderId,
        String riderName,
        String displayName,
        String phone,
        String areaCode,
        String updatedBy
    ) {
        String normalizedAreaCode = areaCode == null || areaCode.isBlank() ? null : areaCode.trim();
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET rider_name = ?,
                    display_name = ?,
                    phone = ?,
                    default_area_code = ?,
                    assigned_by = ?,
                    assigned_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            riderName,
            displayName,
            phone,
            normalizedAreaCode,
            updatedBy,
            riderId
        );
        jdbcTemplate.update(
            "UPDATE dispatch_assignments SET rider_name = ? WHERE rider_profile_id = ?",
            riderName, riderId
        );
        String riderStatus = jdbcTemplate.queryForObject(
            "SELECT auth_status FROM rider_profiles WHERE id = ?",
            String.class,
            riderId
        );
        return new DispatchRiderProfileUpsertResponse(
            riderId,
            riderName,
            displayName,
            phone,
            normalizedAreaCode,
            riderStatus
        );
    }

    @Override
    public DispatchRiderAuthBindingResponse riderAuthBinding(long riderId) {
        return jdbcTemplate.query(
            """
                SELECT
                    id,
                    rider_name,
                    COALESCE(display_name, rider_name) AS display_name,
                    phone,
                    current_openid,
                    auth_status,
                    last_login_at
                FROM rider_profiles
                WHERE id = ?
                """,
            ps -> ps.setLong(1, riderId),
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new DispatchRiderAuthBindingResponse(
                    rs.getLong("id"),
                    rs.getString("rider_name"),
                    rs.getString("display_name"),
                    rs.getString("phone"),
                    rs.getString("current_openid"),
                    rs.getString("auth_status"),
                    formatTimestamp(rs.getTimestamp("last_login_at"))
                );
            }
        );
    }

    @Override
    @Transactional
    public DispatchRiderAuthTakeoverResponse takeoverRiderAuth(long riderId, long sourceRiderId, String assignedBy) {
        DispatchRiderAuthSource source = loadRiderAuthSource(sourceRiderId);
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET phone = COALESCE(?, phone),
                    current_openid = ?,
                    wechat_open_id = ?,
                    display_name = COALESCE(?, display_name),
                    last_login_at = COALESCE(?, last_login_at),
                    auth_status = 'ACTIVE',
                    assigned_by = ?,
                    assigned_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            source.phone(),
            source.currentOpenid(),
            source.wechatOpenId(),
            source.displayName(),
            toTimestamp(source.lastLoginAt()),
            assignedBy,
            riderId
        );
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET current_openid = NULL,
                    wechat_open_id = NULL,
                    auth_status = 'DISABLED',
                    assigned_by = ?,
                    assigned_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            assignedBy,
            sourceRiderId
        );
        return new DispatchRiderAuthTakeoverResponse(
            riderId,
            sourceRiderId,
            source.currentOpenid(),
            "ACTIVE"
        );
    }

    @Override
    @Transactional
    public DispatchRiderAuthUnbindResponse unbindRiderAuth(long riderId, String assignedBy) {
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET current_openid = NULL,
                    wechat_open_id = NULL,
                    auth_status = 'DISABLED',
                    assigned_by = ?,
                    assigned_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            assignedBy,
            riderId
        );
        return new DispatchRiderAuthUnbindResponse(
            riderId,
            "",
            "DISABLED"
        );
    }

    @Override
    public List<DispatchAreaBindingResponse> areaBindings(String mealPeriod, String serveDate) {
        LocalDate targetDate = serveDate == null || serveDate.isEmpty() 
            ? LocalDate.now() 
            : LocalDate.parse(serveDate);
        
        String finalMealPeriod = normalizedMealPeriod(mealPeriod);
        List<DispatchAreaBindingResponse> responses = jdbcTemplate.query(
            """
                SELECT
                    dab.area_code,
                    dab.keywords,
                    dab.default_rider_profile_id,
                    rp_default.rider_name AS default_rider_name,
                    dab.updated_by,
                    dab.updated_at AS updated_at
                FROM dispatch_area_bindings dab
                LEFT JOIN rider_profiles rp_default ON rp_default.id = dab.default_rider_profile_id
                ORDER BY dab.area_code
                """,
            (rs, rowNum) -> {
                String areaCode = rs.getString("area_code");
                List<DispatchAreaOrderItemResponse> orders = findAreaOrders(areaCode, finalMealPeriod, targetDate);
                String currentRiderName = orders.stream()
                    .map(DispatchAreaOrderItemResponse::riderName)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElse(rs.getString("default_rider_name"));
                boolean missingRider = !orders.isEmpty() && (currentRiderName == null || currentRiderName.isBlank());
                return new DispatchAreaBindingResponse(
                    areaCode,
                    rs.getString("keywords"),
                    rs.getObject("default_rider_profile_id") == null ? null : rs.getLong("default_rider_profile_id"),
                    rs.getString("default_rider_name"),
                    missingRider ? null : currentRiderName,
                    orders.stream().mapToInt(item -> Math.max(item.quantity(), 1)).sum(),
                    missingRider,
                    orders,
                    rs.getString("updated_by"),
                    formatTimestamp(rs.getTimestamp("updated_at"))
                );
            }
        );
        return responses;
    }

    @Override
    @Transactional
    public DispatchAreaBindingUpdateResultResponse updateAreaBinding(String areaCode, String keywords, Long defaultRiderId, Long backupRiderId, String updatedBy) {
        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_area_bindings WHERE area_code = ?",
            Integer.class,
            areaCode
        );
        String status = "UPDATED";
        if (existing != null && existing > 0) {
            jdbcTemplate.update(
                """
                    UPDATE dispatch_area_bindings
                    SET keywords = COALESCE(?, keywords),
                        default_rider_profile_id = ?,
                        backup_rider_profile_id = ?,
                        updated_by = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE area_code = ?
                    """,
                keywords,
                defaultRiderId,
                backupRiderId,
                updatedBy,
                areaCode
            );
        } else {
            jdbcTemplate.update(
                """
                    INSERT INTO dispatch_area_bindings (
                        area_code,
                        keywords,
                        default_rider_profile_id,
                        backup_rider_profile_id,
                        updated_by,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                areaCode,
                keywords,
                defaultRiderId,
                backupRiderId,
                updatedBy
            );
            status = "CREATED";
        }
        if (defaultRiderId != null && defaultRiderId > 0) {
            jdbcTemplate.update(
                "UPDATE rider_profiles SET default_area_code = ?, assigned_by = ?, assigned_at = CURRENT_TIMESTAMP WHERE id = ?",
                areaCode,
                updatedBy,
                defaultRiderId
            );
        }
        return new DispatchAreaBindingUpdateResultResponse(
            areaCode,
            keywords,
            defaultRiderId,
            backupRiderId,
            status
        );
    }

    @Override
    public DispatchAreaBindingRemoveResponse removeAreaBinding(String areaCode, long riderId) {
        jdbcTemplate.update(
            "DELETE FROM dispatch_area_bindings WHERE area_code = ? AND default_rider_profile_id = ?",
            areaCode,
            riderId
        );
        return new DispatchAreaBindingRemoveResponse(areaCode, riderId, "REMOVED");
    }

    @Override
    @Transactional
    public DispatchAreaRenameResponse renameArea(String areaCode, String newAreaCode) {
        String trimmed = newAreaCode.trim();
        if (trimmed.isEmpty() || trimmed.equals(areaCode)) {
            throw new IllegalArgumentException("invalid area name");
        }
        jdbcTemplate.update(
            "UPDATE dispatch_area_bindings SET area_code = ? WHERE area_code = ?",
            trimmed, areaCode
        );
        jdbcTemplate.update(
            "UPDATE dispatch_assignments SET area_code = ? WHERE area_code = ?",
            trimmed, areaCode
        );
        jdbcTemplate.update(
            "UPDATE rider_profiles SET default_area_code = ? WHERE default_area_code = ?",
            trimmed, areaCode
        );
        return new DispatchAreaRenameResponse(areaCode, trimmed, "RENAMED");
    }

    @Override
    @Transactional
    public DispatchAreaDeleteResponse deleteArea(String areaCode) {
        List<DispatchAreaBlockingOrderResponse> blockingOrders = jdbcTemplate.query(
            """
                SELECT
                    mso.id AS order_id,
                    c.name AS customer_name,
                    ca.address_line AS delivery_address,
                    da.status AS delivery_status,
                    doo.serve_date AS serve_date
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                JOIN customers c ON c.id = doo.customer_id
                JOIN customer_addresses ca ON ca.id = mso.address_id
                WHERE da.area_code = ?
                  AND da.status IN ('AREA_ASSIGNED', 'DISPATCHING')
                ORDER BY mso.id
                """,
            (rs, rowNum) -> new DispatchAreaBlockingOrderResponse(
                rs.getLong("order_id"),
                rs.getString("customer_name"),
                rs.getString("delivery_address"),
                rs.getString("delivery_status"),
                rs.getDate("serve_date").toLocalDate().toString()
            ),
            areaCode
        );
        if (!blockingOrders.isEmpty()) {
            throw new BusinessException(
                ErrorCode.DISPATCH_AREA_HAS_ACTIVE_ORDERS,
                "区域“" + areaCode + "”还有 " + blockingOrders.size() + " 个配送中的订单，暂不能删除",
                Map.of(
                    "areaCode", areaCode,
                    "activeOrderCount", blockingOrders.size(),
                    "orders", blockingOrders
                )
            );
        }
        jdbcTemplate.update(
            "UPDATE rider_profiles SET default_area_code = NULL WHERE default_area_code = ?",
            areaCode
        );
        jdbcTemplate.update(
            "DELETE FROM dispatch_area_bindings WHERE area_code = ?",
            areaCode
        );
        return new DispatchAreaDeleteResponse(areaCode, "DELETED");
    }

    @Override
    public List<DispatchReassignmentResponse> recentReassignments(String serveDate) {
        LocalDateTime retentionCutoff = LocalDateTime.now().minusDays(DISPATCH_REASSIGNMENT_RETENTION_DAYS);
        StringBuilder sql = new StringBuilder(
            """
                SELECT
                    id,
                    reassign_level,
                    target_id,
                    from_rider_name,
                    to_rider_name,
                    to_area_code,
                    serve_date,
                    meal_period,
                    sync_default_binding,
                    reason,
                    created_by,
                    created_at
                FROM dispatch_reassignments
                WHERE created_at >= ?
                """
        );
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(retentionCutoff));
        if (serveDate != null && !serveDate.isBlank()) {
            sql.append(" AND serve_date = ?");
            args.add(LocalDate.parse(serveDate));
        }
        sql.append(" ORDER BY created_at DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DispatchReassignmentResponse(
            rs.getLong("id"),
            rs.getString("reassign_level"),
            rs.getLong("target_id"),
            rs.getString("from_rider_name"),
            rs.getString("to_rider_name"),
            rs.getString("to_area_code"),
            rs.getDate("serve_date").toLocalDate().toString(),
            rs.getString("meal_period"),
            rs.getBoolean("sync_default_binding"),
            rs.getString("reason"),
            rs.getString("created_by"),
            formatTimestamp(rs.getTimestamp("created_at"))
        ), args.toArray());
    }

    @Override
    @Transactional
    public DispatchAreaRiderAssignResponse assignRiderToArea(String areaCode, String riderName, String updatedBy, String mealPeriod) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        String finalMealPeriod = normalizedMealPeriod(mealPeriod);
        
        List<Long> orderIds = jdbcTemplate.query(
            """
                SELECT da.meal_slot_order_id
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                WHERE da.area_code = ?
                  AND da.status IN ('AREA_ASSIGNED', 'DISPATCHING')
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                ORDER BY da.sequence_number, da.id
                """,
            (rs, rowNum) -> rs.getLong("meal_slot_order_id"),
            normalizedAreaCode,
            finalMealPeriod,
            finalMealPeriod
        );
        
        int successCount = 0;
        for (Long orderId : orderIds) {
            dispatchOrder(orderId, resolveAssignmentRiderName(normalizedAreaCode, riderName), normalizedAreaCode, true);
            successCount++;
        }
        publishDispatchEvent("dispatch.queue.changed", normalizedAreaCode, riderName, null);
        return new DispatchAreaRiderAssignResponse(
            normalizedAreaCode,
            successCount,
            finalMealPeriod == null ? "" : finalMealPeriod
        );
    }

    @Override
    @Transactional
    public DispatchAreaOrderAssignResponse assignRiderToAreaOrder(String areaCode, long orderId, String riderName, String updatedBy) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        dispatchOrder(orderId, resolveAssignmentRiderName(normalizedAreaCode, riderName), normalizedAreaCode, true);
        publishDispatchEvent("dispatch.queue.changed", normalizedAreaCode, riderName, orderId);
        return new DispatchAreaOrderAssignResponse(normalizedAreaCode, orderId, "DISPATCHED");
    }

    @Override
    @Transactional
    public DispatchAreaOrdersReorderResponse reorderAreaOrders(String areaCode, List<DispatchOrderReorderItemRequest> items) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        if (items == null || items.isEmpty()) {
            return new DispatchAreaOrdersReorderResponse(normalizedAreaCode, 0);
        }

        List<Long> orderIds = items.stream()
            .map(DispatchOrderReorderItemRequest::orderId)
            .filter(id -> id != null && id > 0)
            .toList();
        if (!orderIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(orderIds.size(), "?"));
            List<Object> tempArgs = new ArrayList<>(orderIds.size());
            tempArgs.addAll(orderIds);
            jdbcTemplate.update(
                "UPDATE dispatch_batch_items SET current_sequence = current_sequence + 1000 WHERE manually_adjusted = FALSE AND meal_slot_order_id IN (" + placeholders + ")",
                tempArgs.toArray()
            );
        }

        int updatedCount = 0;
        for (DispatchOrderReorderItemRequest item : items) {
            jdbcTemplate.update(
                "UPDATE dispatch_assignments SET sequence_number = ? WHERE meal_slot_order_id = ? AND area_code = ?",
                item.sequenceNumber(),
                item.orderId(),
                normalizedAreaCode
            );
            jdbcTemplate.update(
                "UPDATE dispatch_batch_items SET current_sequence = ? WHERE meal_slot_order_id = ? AND manually_adjusted = FALSE",
                item.sequenceNumber(),
                item.orderId()
            );
            updatedCount++;
        }
        publishDispatchEvent("dispatch.queue.changed", normalizedAreaCode, null, items.get(0).orderId());
        return new DispatchAreaOrdersReorderResponse(normalizedAreaCode, updatedCount);
    }

    @Override
    @Transactional
    public DispatchOrderAreaMoveResponse moveOrderToArea(String areaCode, long orderId, String targetAreaCode, String updatedBy) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        String normalizedTargetAreaCode = requireAreaCode(targetAreaCode);
        DispatchOrderContext orderContext = loadOrderContext(orderId);

        List<Long> batchIds = jdbcTemplate.query(
            "SELECT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
            (rs, rowNum) -> rs.getLong("batch_id"),
            orderId
        );
        if (!batchIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE meal_slot_order_id = ?", orderId);
            refreshBatchMetrics(batchIds.get(0));
        }

        jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'PENDING_DISPATCH' WHERE id = ?", orderId);

        jdbcTemplate.update(
            """
                UPDATE dispatch_assignments
                SET area_code = ?,
                    sequence_number = ?,
                    rider_name = NULL,
                    rider_profile_id = NULL,
                    status = 'AREA_ASSIGNED'
                WHERE meal_slot_order_id = ? AND area_code = ?
                """,
            normalizedTargetAreaCode,
            nextAreaSequence(
                normalizedTargetAreaCode,
                orderContext.serveDate(),
                orderContext.mealPeriod()
            ),
            orderId,
            normalizedAreaCode
        );
        syncAddressBindingForArea(orderId, normalizedTargetAreaCode, updatedBy, "AREA_MOVED");
        publishDispatchEvent("dispatch.assignment.changed", normalizedTargetAreaCode, null, orderId);
        return new DispatchOrderAreaMoveResponse(normalizedAreaCode, orderId, normalizedTargetAreaCode);
    }

    @Override
    @Transactional
    public DispatchReassignResultResponse reassignDispatch(
        String reassignLevel,
        long targetId,
        String fromRiderName,
        String toRiderName,
        String toAreaCode,
        String serveDate,
        String mealPeriod,
        boolean syncDefaultBinding,
        String reason,
        String createdBy
    ) {
        String finalAreaCode = normalizeAreaCode(toAreaCode, toRiderName);
        List<Long> orderIds = findOrderIdsForReassign(reassignLevel, targetId, serveDate, mealPeriod, finalAreaCode, fromRiderName);
        for (Long orderId : orderIds) {
            dispatchOrder(orderId, toRiderName, finalAreaCode, syncDefaultBinding);
        }
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_reassignments (
                    reassign_level,
                    target_id,
                    from_rider_name,
                    to_rider_name,
                    to_area_code,
                    serve_date,
                    meal_period,
                    sync_default_binding,
                    reason,
                    created_by,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
            reassignLevel,
            targetId,
            fromRiderName,
            toRiderName,
            finalAreaCode,
            serveDate,
            mealPeriod,
            syncDefaultBinding,
            reason,
            createdBy
        );
        pruneOldDispatchReassignments();
        if (syncDefaultBinding && finalAreaCode != null && !finalAreaCode.isBlank()) {
            Long riderId = findRiderProfileIdByName(toRiderName);
            if (riderId != null) {
                updateAreaBinding(finalAreaCode, null, riderId, null, createdBy);
            }
        }
        publishDispatchEvent("dispatch.assignment.changed", finalAreaCode, toRiderName, targetId);
        return new DispatchReassignResultResponse(
            reassignLevel,
            targetId,
            toRiderName,
            finalAreaCode,
            syncDefaultBinding,
            orderIds.size()
        );
    }

    @Override
    @Transactional
    public DispatchRiderActivateResponse activateRider(long riderId, String riderName, String areaCode, String assignedBy) {
        String normalizedAreaCode = areaCode == null || areaCode.isBlank() ? null : areaCode.trim();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET rider_name = ?,
                    display_name = ?,
                    default_area_code = ?,
                    auth_status = 'ACTIVE',
                    assigned_at = ?,
                    assigned_by = ?
                WHERE id = ?
                """,
            riderName,
            riderName,
            normalizedAreaCode,
            Timestamp.valueOf(now),
            assignedBy,
            riderId
        );
        if (normalizedAreaCode != null) {
            updateAreaBinding(normalizedAreaCode, null, riderId, null, assignedBy);
        }
        return new DispatchRiderActivateResponse(riderId, "ACTIVE", normalizedAreaCode);
    }

    private void pruneOldDispatchReassignments() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(DISPATCH_REASSIGNMENT_RETENTION_DAYS);
        jdbcTemplate.update(
            """
            DELETE FROM dispatch_reassignments
            WHERE created_at < ?
            """,
            Timestamp.valueOf(cutoffTime)
        );
    }

    @Override
    @Transactional
    public DispatchRiderStatusResponse disableRider(long riderId, String assignedBy) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET auth_status = 'DISABLED',
                    assigned_at = COALESCE(assigned_at, ?),
                    assigned_by = COALESCE(assigned_by, ?)
                WHERE id = ?
                """,
            Timestamp.valueOf(now),
            assignedBy,
            riderId
        );
        return new DispatchRiderStatusResponse(
            riderId,
            "DISABLED"
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

    private int queryCount(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private int autoAssignRememberedPendingOrders(String mealPeriod) {
        String finalMealPeriod = normalizedMealPeriod(mealPeriod);
        List<RememberedPendingOrderRow> orders = jdbcTemplate.query(
            """
                SELECT
                    mso.id AS order_id,
                    doo.serve_date,
                    mso.meal_period,
                    rab.area_code
                FROM meal_slot_orders mso
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                JOIN rider_address_bindings rab ON rab.customer_id = doo.customer_id AND rab.address_id = mso.address_id
                JOIN dispatch_area_bindings dab ON dab.area_code = rab.area_code
                LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id = mso.id
                WHERE mso.status = 'PENDING_DISPATCH'
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                  AND da.id IS NULL
                ORDER BY mso.id
                """,
            (rs, rowNum) -> new RememberedPendingOrderRow(
                rs.getLong("order_id"),
                rs.getDate("serve_date").toLocalDate(),
                rs.getString("meal_period"),
                rs.getString("area_code")
            ),
            finalMealPeriod,
            finalMealPeriod
        );
        int assignedCount = 0;
        for (RememberedPendingOrderRow order : orders) {
            long orderId = order.orderId();
            String areaCode = order.areaCode();
            if (areaCode == null || areaCode.isBlank()) {
                continue;
            }
            String defaultRiderName = resolveDefaultRiderName(areaCode);
            if (defaultRiderName != null) {
                dispatchOrder(orderId, defaultRiderName, areaCode, true);
            } else {
                int sequenceNumber = nextAreaSequence(
                    areaCode,
                    order.serveDate(),
                    order.mealPeriod()
                );
                insertAndReturnId(
                    """
                        INSERT INTO dispatch_assignments (
                            meal_slot_order_id,
                            rider_name,
                            rider_profile_id,
                            area_code,
                            status,
                            sequence_number,
                            created_at
                        ) VALUES (?, NULL, NULL, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                    orderId,
                    areaCode,
                    "AREA_ASSIGNED",
                    sequenceNumber
                );
            }
            assignedCount++;
        }
        return assignedCount;
    }

    private int nextAreaSequence(String areaCode, LocalDate serveDate, String mealPeriod) {
        Integer sequence = jdbcTemplate.queryForObject(
            """
                SELECT COALESCE(MAX(da.sequence_number), 0) + 1
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE da.area_code = ?
                  AND doo.serve_date = ?
                  AND COALESCE(mso.delivery_meal_period, mso.meal_period) = ?
                """,
            Integer.class,
            areaCode,
            serveDate,
            mealPeriod
        );
        return sequence == null ? 1 : sequence;
    }

    private DispatchOrderContext loadOrderContext(long orderId) {
        return jdbcTemplate.query(
            """
                SELECT
                    doo.customer_id,
                    doo.serve_date,
                    mso.address_id,
                    mso.meal_period,
                    ca.address_line
                FROM meal_slot_orders mso
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                JOIN customer_addresses ca ON ca.id = mso.address_id
                WHERE mso.id = ?
                """,
            ps -> ps.setLong(1, orderId),
            rs -> {
                if (!rs.next()) {
                    throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "未找到对应订单");
                }
                return new DispatchOrderContext(
                    rs.getLong("customer_id"),
                    rs.getDate("serve_date").toLocalDate(),
                    rs.getLong("address_id"),
                    rs.getString("meal_period"),
                    rs.getString("address_line")
                );
            }
        );
    }

    private void syncAddressBindingForArea(long orderId, String areaCode, String updatedBy, String updatedReason) {
        DispatchOrderContext orderContext = loadOrderContext(orderId);
        long customerId = orderContext.customerId();
        long addressId = orderContext.addressId();
        String addressLine = orderContext.addressLine();
        String fingerprint = normalizeAddressFingerprint(addressLine);
        Long riderProfileId = resolveDefaultRiderProfileId(areaCode);
        int existing = queryCount(
            "SELECT COUNT(*) FROM rider_address_bindings WHERE customer_id = ? AND address_id = ?",
            customerId,
            addressId
        );
        if (existing > 0) {
            jdbcTemplate.update(
                """
                    UPDATE rider_address_bindings
                    SET address_fingerprint = ?,
                        area_code = ?,
                        rider_profile_id = ?,
                        manually_confirmed = TRUE,
                        updated_reason = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE customer_id = ? AND address_id = ?
                    """,
                fingerprint,
                areaCode,
                riderProfileId,
                updatedReason,
                customerId,
                addressId
            );
            return;
        }
        jdbcTemplate.update(
            """
                INSERT INTO rider_address_bindings (
                    customer_id,
                    address_id,
                    address_fingerprint,
                    area_code,
                    rider_profile_id,
                    manually_confirmed,
                    updated_reason,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, TRUE, ?, CURRENT_TIMESTAMP)
                """,
            customerId,
            addressId,
            fingerprint,
            areaCode,
            riderProfileId,
            updatedReason
        );
    }

    private Long resolveDefaultRiderProfileId(String areaCode) {
        List<Long> riderIds = jdbcTemplate.query(
            """
                SELECT COALESCE(default_rider_profile_id, backup_rider_profile_id) AS rider_profile_id
                FROM dispatch_area_bindings
                WHERE area_code = ?
                  AND COALESCE(default_rider_profile_id, backup_rider_profile_id) IS NOT NULL
                """,
            (rs, rowNum) -> rs.getLong("rider_profile_id"),
            areaCode
        );
        return riderIds.isEmpty() ? null : riderIds.get(0);
    }

    private String normalizedMealPeriod(String mealPeriod) {
        return mealPeriod == null || mealPeriod.isBlank() ? null : mealPeriod.trim().toUpperCase();
    }

    private List<DispatchAreaOrderItemResponse> findAreaOrders(String areaCode, String mealPeriod, LocalDate serveDate) {
        List<DispatchAreaOrderRow> rows = jdbcTemplate.query(
            """
                SELECT
                    mso.id AS order_id,
                    COALESCE(NULLIF(da.sequence_number, 0), dbi.current_sequence, 0) AS sequence_number,
                    c.name AS customer_name,
                    c.phone AS customer_phone,
                    ca.address_line AS delivery_address,
                    da.status AS delivery_status,
                    da.rider_name,
                    COALESCE(mso.user_note, mso.note, '') AS user_note,
                    COALESCE(mso.merchant_remark, '') AS merchant_remark,
                    COALESCE(ari.reference_image_url, '') AS reference_image_url,
                    COALESCE(dr.receipt_url, '') AS receipt_url,
                    COALESCE(dr.receipt_note, '') AS receipt_note,
                    dr.delivered_at,
                    mso.quantity
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                JOIN customers c ON c.id = doo.customer_id
                JOIN customer_addresses ca ON ca.id = mso.address_id
                LEFT JOIN dispatch_batch_items dbi ON dbi.meal_slot_order_id = da.meal_slot_order_id
                LEFT JOIN address_reference_images ari ON ari.customer_address_id = mso.address_id
                LEFT JOIN (
                    SELECT meal_slot_order_id, receipt_url, receipt_note, delivered_at
                    FROM delivery_receipts dr_inner
                    WHERE dr_inner.id = (
                        SELECT MAX(id) FROM delivery_receipts WHERE meal_slot_order_id = dr_inner.meal_slot_order_id
                    )
                ) dr ON dr.meal_slot_order_id = mso.id
                WHERE da.area_code = ?
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                ORDER BY 
                    CASE WHEN da.status = 'DELIVERED' THEN 1 ELSE 0 END,
                    COALESCE(NULLIF(da.sequence_number, 0), dbi.current_sequence, 0),
                    da.id
                """,
            (rs, rowNum) -> new DispatchAreaOrderRow(
                rs.getLong("order_id"),
                rs.getInt("sequence_number"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                rs.getString("delivery_address"),
                rs.getString("delivery_status"),
                rs.getString("rider_name"),
                rs.getString("user_note"),
                rs.getString("merchant_remark"),
                rs.getString("reference_image_url"),
                rs.getString("receipt_url"),
                rs.getString("receipt_note"),
                rs.getTimestamp("delivered_at"),
                rs.getInt("quantity")
            ),
            areaCode,
            serveDate,
            mealPeriod,
            mealPeriod
        );
        Map<Long, OrderNoteProjection> projections = loadOrderNoteProjections(
            rows.stream().map(DispatchAreaOrderRow::orderId).toList()
        );
        return rows.stream()
            .map(row -> toDispatchAreaOrderItem(row, projections.get(row.orderId())))
            .toList();
    }

    private DispatchAreaOrderItemResponse toDispatchAreaOrderItem(DispatchAreaOrderRow row, OrderNoteProjection projection) {
        String userNote = resolveProjectedUserNote(projection, row.userNote());
        String merchantRemark = resolveProjectedAdminNote(projection, row.merchantRemark());
        List<String> attentionSources = buildAttentionSources(userNote, merchantRemark);
        return new DispatchAreaOrderItemResponse(
            row.orderId(),
            row.sequenceNumber(),
            row.customerName(),
            row.customerPhone(),
            row.deliveryAddress(),
            row.deliveryStatus(),
            row.riderName(),
            userNote,
            merchantRemark,
            !attentionSources.isEmpty(),
            attentionSources,
            buildAttentionLabel(attentionSources),
            row.referenceImageUrl(),
            row.receiptUrl(),
            row.receiptNote(),
            formatTimestamp(row.deliveredAt()),
            row.quantity()
        );
    }

    private Map<String, Integer> loadRiderExceptionCounts(LocalDate serveDate, String mealPeriod) {
        List<RiderExceptionCountRow> rows = jdbcTemplate.query(
            """
                SELECT
                    de.rider_name,
                    da.area_code,
                    COUNT(DISTINCT de.meal_slot_order_id) AS exception_count
                FROM delivery_exceptions de
                JOIN dispatch_assignments da ON da.meal_slot_order_id = de.meal_slot_order_id
                JOIN meal_slot_orders mso ON mso.id = de.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE de.resolved = FALSE
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                GROUP BY de.rider_name, da.area_code
                """,
            (rs, rowNum) -> new RiderExceptionCountRow(
                rs.getString("rider_name"),
                rs.getString("area_code"),
                rs.getInt("exception_count")
            ),
            serveDate,
            mealPeriod,
            mealPeriod
        );
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RiderExceptionCountRow row : rows) {
            counts.put(
                riderProgressKey(row.riderName(), row.areaCode()),
                row.exceptionCount()
            );
        }
        return counts;
    }

    private void markDispatchExceptionResolved(long mealSlotOrderId, String resolvedBy) {
        int updatedCount = jdbcTemplate.update(
            """
                UPDATE delivery_exceptions
                SET resolved = TRUE,
                    resolved_at = CURRENT_TIMESTAMP,
                    resolved_by = ?,
                    resolution_note = CASE
                        WHEN resolution_note IS NULL OR resolution_note = '' THEN '已重新派单处理'
                        ELSE resolution_note
                    END
                WHERE meal_slot_order_id = ?
                  AND resolved = FALSE
                """,
            riderNameOrDefault(resolvedBy),
            mealSlotOrderId
        );
        if (updatedCount > 0) {
            pruneResolvedDispatchExceptions();
        }
    }

    private void pruneResolvedDispatchExceptions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(DISPATCH_EXCEPTION_RETENTION_DAYS);
        jdbcTemplate.update(
            """
                DELETE FROM delivery_exceptions
                WHERE resolved = TRUE
                  AND COALESCE(resolved_at, created_at) < ?
                """,
            Timestamp.valueOf(cutoffTime)
        );
    }

    private String riderNameOrDefault(String riderName) {
        return riderName == null || riderName.isBlank() ? "SYSTEM" : riderName.trim();
    }

    private Map<Long, OrderNoteProjection> loadOrderNoteProjections(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", orderIds.stream().map(id -> "?").toList());
        List<OrderNoteRow> rows = jdbcTemplate.query(
            """
                SELECT meal_slot_order_id, note_type, content
                FROM order_notes
                WHERE effective_status = 'ACTIVE'
                  AND meal_slot_order_id IN (
            """
                + placeholders
                + """
                  )
                ORDER BY meal_slot_order_id, created_at, id
                """,
            (rs, rowNum) -> new OrderNoteRow(
                rs.getLong("meal_slot_order_id"),
                rs.getString("note_type"),
                rs.getString("content")
            ),
            orderIds.toArray()
        );
        Map<Long, DispatchNoteAccumulator> accumulators = new LinkedHashMap<>();
        for (OrderNoteRow row : rows) {
            long orderId = row.orderId();
            DispatchNoteAccumulator accumulator = accumulators.computeIfAbsent(orderId, ignored -> new DispatchNoteAccumulator());
            accumulator.add(row.noteType(), row.content());
        }
        Map<Long, OrderNoteProjection> projections = new LinkedHashMap<>();
        for (Long orderId : orderIds) {
            DispatchNoteAccumulator accumulator = accumulators.get(orderId);
            if (accumulator != null) {
                projections.put(orderId, accumulator.toProjection());
            }
        }
        return projections;
    }

    private String resolveProjectedUserNote(OrderNoteProjection projection, String legacyValue) {
        if (projection != null && projection.hasOrderNotes()) {
            return projection.userNote().isBlank() ? "-" : projection.userNote();
        }
        String normalized = normalizeLegacyNote(legacyValue);
        return normalized.isBlank() ? "-" : normalized;
    }

    private String resolveProjectedAdminNote(OrderNoteProjection projection, String legacyValue) {
        if (projection != null && projection.hasOrderNotes()) {
            return projection.adminNote();
        }
        return normalizeLegacyNote(legacyValue);
    }

    private String normalizeLegacyNote(String value) {
        String normalized = stringValue(value);
        return "-".equals(normalized) ? "" : normalized;
    }

    private List<String> buildAttentionSources(String userNote, String adminNote) {
        List<String> sources = new ArrayList<>();
        if (!normalizeLegacyNote(userNote).isBlank()) {
            sources.add("USER_NOTE");
        }
        if (!normalizeLegacyNote(adminNote).isBlank()) {
            sources.add("MERCHANT_NOTE");
        }
        return List.copyOf(sources);
    }

    private String buildAttentionLabel(List<String> attentionSources) {
        return attentionSources.isEmpty() ? "" : "闇€鐣欐剰";
    }

    private String riderProgressKey(String riderName, String areaCode) {
        return riderName + "@" + areaCode;
    }

    private Long findRiderProfileIdByName(String riderName) {
        List<Long> ids = jdbcTemplate.query(
            "SELECT id FROM rider_profiles WHERE rider_name = ?",
            (rs, rowNum) -> rs.getLong("id"),
            riderName
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private DispatchMatchResult matchRiderForOrder(long orderId) {
        RememberedOrderContext row = jdbcTemplate.query("""
            SELECT
                doo.customer_id,
                mso.address_id,
                rab.area_code AS remembered_area_code
            FROM meal_slot_orders mso
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            LEFT JOIN rider_address_bindings rab ON rab.customer_id = doo.customer_id AND rab.address_id = mso.address_id
            WHERE mso.id = ?
            """,
            ps -> ps.setLong(1, orderId),
            rs -> {
                if (!rs.next()) {
                    throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "未找到对应订单");
                }
                return new RememberedOrderContext(
                    rs.getLong("customer_id"),
                    rs.getLong("address_id"),
                    rs.getString("remembered_area_code")
                );
            }
        );
        String rememberedAreaCode = row.rememberedAreaCode();
        if (rememberedAreaCode == null || rememberedAreaCode.isBlank()) {
            return new DispatchMatchResult(false, null, null);
        }

        List<DispatchMatchResult> matches = jdbcTemplate.query(
            """
                SELECT dab.area_code, rp.rider_name
                FROM dispatch_area_bindings dab
                JOIN rider_profiles rp ON rp.id = dab.default_rider_profile_id
                WHERE dab.area_code = ?
                  AND rp.auth_status = 'ACTIVE'
                  AND rp.employment_status = 'ACTIVE'
                UNION ALL
                SELECT dab.area_code, rp.rider_name
                FROM dispatch_area_bindings dab
                JOIN rider_profiles rp ON rp.id = dab.backup_rider_profile_id
                WHERE dab.area_code = ?
                  AND dab.backup_rider_profile_id IS NOT NULL
                  AND rp.auth_status = 'ACTIVE'
                  AND rp.employment_status = 'ACTIVE'
                """,
            (rs, rowNum) -> new DispatchMatchResult(
                true,
                rs.getString("rider_name"),
                rs.getString("area_code")
            ),
            rememberedAreaCode,
            rememberedAreaCode
        );
        return matches.isEmpty()
            ? new DispatchMatchResult(false, null, rememberedAreaCode)
            : matches.get(0);
    }

    private String requireAreaCode(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "璇烽€夋嫨鍖哄煙");
        }
        return areaCode.trim();
    }

    private String resolveDefaultRiderName(String areaCode) {
        List<String> riderNames = jdbcTemplate.query(
            """
                SELECT rp.rider_name
                FROM dispatch_area_bindings dab
                JOIN rider_profiles rp ON rp.id = dab.default_rider_profile_id
                WHERE dab.area_code = ?
                  AND rp.auth_status = 'ACTIVE'
                  AND rp.employment_status = 'ACTIVE'
                """,
            (rs, rowNum) -> rs.getString("rider_name"),
            areaCode
        );
        return riderNames.isEmpty() ? null : riderNames.get(0);
    }

    private String resolveAssignmentRiderName(String areaCode, String riderName) {
        if (riderName != null && !riderName.isBlank()) {
            return riderName.trim();
        }
        List<String> riderNames = jdbcTemplate.query(
            """
                SELECT rp.rider_name
                FROM dispatch_area_bindings dab
                JOIN rider_profiles rp ON rp.id = dab.default_rider_profile_id
                WHERE dab.area_code = ?
                  AND rp.auth_status = 'ACTIVE'
                  AND rp.employment_status = 'ACTIVE'
                """,
            (rs, rowNum) -> rs.getString("rider_name"),
            areaCode
        );
        if (!riderNames.isEmpty()) {
            return riderNames.get(0);
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "鎵€閫夊尯鍩熸殏鏈粦瀹氬彲娲惧崟楠戞墜锛岃鍏堟寚瀹氶獞鎵嬫垨鍙綊鍖哄煙");
    }

    private String resolveRiderName(long riderProfileId) {
        List<String> riderNames = jdbcTemplate.query(
            "SELECT rider_name FROM rider_profiles WHERE id = ?",
            (rs, rowNum) -> rs.getString("rider_name"),
            riderProfileId
        );
        if (!riderNames.isEmpty()) {
            return riderNames.get(0);
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "鏈壘鍒板彲鐢ㄤ簬褰掑尯鍩熺殑楠戞墜妗ｆ");
    }

    private long ensureRiderProfile(String riderName, String areaCode) {
        Long profileId = findRiderProfileIdByName(riderName);
        if (profileId != null) {
            jdbcTemplate.update(
                "UPDATE rider_profiles SET default_area_code = COALESCE(?, default_area_code), employment_status = 'ACTIVE', auth_status = COALESCE(auth_status, 'ACTIVE') WHERE id = ?",
                areaCode,
                profileId
            );
            return profileId;
        }
        return insertAndReturnId(
            "INSERT INTO rider_profiles (rider_name, employment_status, default_area_code, auth_status) VALUES (?, ?, ?, ?)",
            riderName,
            "ACTIVE",
            areaCode,
            "ACTIVE"
        );
    }

    private void syncAddressBinding(long orderId, long riderProfileId, String areaCode) {
        DispatchOrderContext orderRow = loadOrderContext(orderId);
        long customerId = orderRow.customerId();
        long addressId = orderRow.addressId();
        String addressLine = orderRow.addressLine();
        String fingerprint = normalizeAddressFingerprint(addressLine);
        int existing = queryCount("SELECT COUNT(*) FROM rider_address_bindings WHERE customer_id = " + customerId + " AND address_id = " + addressId);
        if (existing > 0) {
            jdbcTemplate.update(
                "UPDATE rider_address_bindings SET address_fingerprint = ?, area_code = ?, rider_profile_id = ?, manually_confirmed = TRUE, updated_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE customer_id = ? AND address_id = ?",
                fingerprint,
                areaCode,
                riderProfileId,
                "AREA_CONFIRMED",
                customerId,
                addressId
            );
            return;
        }
        jdbcTemplate.update(
            "INSERT INTO rider_address_bindings (customer_id, address_id, address_fingerprint, area_code, rider_profile_id, manually_confirmed, updated_reason, updated_at) VALUES (?, ?, ?, ?, ?, TRUE, ?, CURRENT_TIMESTAMP)",
            customerId,
            addressId,
            fingerprint,
            areaCode,
            riderProfileId,
            "AREA_CONFIRMED"
        );
    }

    private int ensureBatchItem(long orderId, long riderProfileId, String areaCode) {
        DispatchOrderContext row = loadOrderContext(orderId);
        LocalDate serveDate = row.serveDate();
        String mealPeriod = row.mealPeriod();
        List<Long> batchIds = jdbcTemplate.query(
            "SELECT id FROM dispatch_batches WHERE serve_date = ? AND meal_period = ? AND rider_profile_id = ? AND area_code = ?",
            (rs, rowNum) -> rs.getLong("id"),
            serveDate,
            mealPeriod,
            riderProfileId,
            areaCode
        );
        long batchId;
        if (batchIds.isEmpty()) {
            batchId = insertAndReturnId(
                "INSERT INTO dispatch_batches (serve_date, meal_period, rider_profile_id, area_code, batch_status, total_count, delivered_count, current_sequence) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                serveDate,
                mealPeriod,
                riderProfileId,
                areaCode,
                "READY",
                0,
                0,
                1
            );
        } else {
            batchId = batchIds.get(0);
        }
        int finalSequence;
        List<Long> existingBatchIds = jdbcTemplate.query(
            "SELECT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
            (rs, rowNum) -> rs.getLong("batch_id"),
            orderId
        );
        if (existingBatchIds.isEmpty()) {
            int nextSequence = nextBatchSequence(batchId);
            jdbcTemplate.update(
                "INSERT IGNORE INTO dispatch_batch_items (batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted) VALUES (?, ?, ?, ?, ?, FALSE)",
                batchId,
                orderId,
                nextSequence,
                nextSequence,
                "PENDING"
            );
            finalSequence = nextSequence;
        } else if (existingBatchIds.get(0) != batchId) {
            long previousBatchId = existingBatchIds.get(0);
            int nextSequence = nextBatchSequence(batchId);
            jdbcTemplate.update(
                """
                    UPDATE dispatch_batch_items
                    SET batch_id = ?,
                        current_sequence = ?,
                        suggested_sequence = ?,
                        manually_adjusted = TRUE,
                        reordered_by = ?,
                        reordered_at = CURRENT_TIMESTAMP
                    WHERE meal_slot_order_id = ?
                    """,
                batchId,
                nextSequence,
                nextSequence,
                "ADMIN_REASSIGN",
                orderId
            );
            refreshBatchMetrics(previousBatchId);
            finalSequence = nextSequence;
        } else {
            Integer currentSequence = jdbcTemplate.queryForObject(
                "SELECT current_sequence FROM dispatch_batch_items WHERE meal_slot_order_id = ?",
                Integer.class,
                orderId
            );
            finalSequence = currentSequence == null ? 1 : currentSequence;
        }
        refreshBatchMetrics(batchId);
        return finalSequence;
    }

    private int nextBatchSequence(long batchId) {
        Integer sequence = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(current_sequence), 0) + 1 FROM dispatch_batch_items WHERE batch_id = ?",
            Integer.class,
            batchId
        );
        return sequence == null ? 1 : sequence;
    }

    private void publishDispatchEvent(String eventType, String areaCode, String riderName, Object orderId) {
        RealtimeEvent.Builder builder = RealtimeEvent.builder(eventType)
            .audience("admin")
            .audience("rider:all");
        if (areaCode != null && !areaCode.isBlank()) {
            builder.payload("areaCode", areaCode);
        }
        if (riderName != null && !riderName.isBlank()) {
            builder.audience("rider:name:" + riderName.trim()).payload("riderName", riderName.trim());
        }
        if (orderId != null) {
            builder.payload("orderId", orderId);
        }
        realtimeEventPublisher.publish(builder.build());
    }

    private void refreshBatchMetrics(long batchId) {
        jdbcTemplate.update(
            """
                UPDATE dispatch_batches
                SET total_count = (
                        SELECT COALESCE(SUM(mso.quantity), 0)
                        FROM dispatch_batch_items dbi
                        JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
                        WHERE dbi.batch_id = ?
                    ),
                    delivered_count = (
                        SELECT COALESCE(SUM(mso.quantity), 0)
                        FROM dispatch_batch_items dbi
                        JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
                        WHERE dbi.batch_id = ?
                          AND dbi.item_status = 'DELIVERED'
                    )
                WHERE id = ?
                """,
            batchId,
            batchId,
            batchId
        );
    }

    private List<Long> findOrderIdsForReassign(
        String reassignLevel,
        long targetId,
        String serveDate,
        String mealPeriod,
        String areaCode,
        String fromRiderName
    ) {
        if ("ORDER".equalsIgnoreCase(reassignLevel)) {
            return List.of(targetId);
        }
        if ("BATCH".equalsIgnoreCase(reassignLevel)) {
            return jdbcTemplate.query(
                "SELECT meal_slot_order_id FROM dispatch_batch_items WHERE batch_id = ? ORDER BY current_sequence",
                (rs, rowNum) -> rs.getLong("meal_slot_order_id"),
                targetId
            );
        }
        StringBuilder sql = new StringBuilder(
            """
                SELECT da.meal_slot_order_id
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE doo.serve_date = ?
                """
        );
        List<Object> args = new ArrayList<>();
        args.add(LocalDate.parse(serveDate));
        if (mealPeriod != null && !mealPeriod.isBlank()) {
            sql.append(" AND COALESCE(mso.delivery_meal_period, mso.meal_period) = ?");
            args.add(mealPeriod);
        }
        if (areaCode != null && !areaCode.isBlank()) {
            sql.append(" AND da.area_code = ?");
            args.add(areaCode);
        }
        if (fromRiderName != null && !fromRiderName.isBlank()) {
            sql.append(" AND da.rider_name = ?");
            args.add(fromRiderName);
        }
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> rs.getLong("meal_slot_order_id"), args.toArray());
    }

    private String normalizeAreaCode(String areaCode, String riderName) {
        if (areaCode != null && !areaCode.isBlank()) {
            return areaCode;
        }
        if (riderName != null && !riderName.isBlank()) {
            List<String> areaCodes = jdbcTemplate.query(
                "SELECT default_area_code FROM rider_profiles WHERE rider_name = ?",
                (rs, rowNum) -> rs.getString("default_area_code"),
                riderName
            );
            if (!areaCodes.isEmpty() && areaCodes.get(0) != null && !areaCodes.get(0).isBlank()) {
                return areaCodes.get(0);
            }
        }
        return null;
    }

    private Timestamp parseTimestampOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Timestamp.valueOf(value);
    }

    private Timestamp toTimestamp(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp;
        }
        if (value instanceof LocalDateTime dateTime) {
            return Timestamp.valueOf(dateTime);
        }
        return parseTimestampOrNull(String.valueOf(value));
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

    private String formatTimestamp(Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeAddressFingerprint(String addressLine) {
        if (addressLine == null) {
            return "";
        }
        return addressLine.replace(" ", "").replace("-", "").replace("，", "").replace(",", "");
    }

    private DispatchNotificationContext loadDispatchNotificationContext(long dispatchId) {
        return jdbcTemplate.query(
            """
                SELECT c.id AS customer_id
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders do ON do.id = mso.daily_order_id
                JOIN customers c ON c.id = do.customer_id
                WHERE da.id = ?
                """,
            ps -> ps.setLong(1, dispatchId),
            rs -> {
                if (!rs.next()) {
                    throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "未找到对应订单");
                }
                return new DispatchNotificationContext(rs.getLong("customer_id"));
            }
        );
    }

    private DispatchRiderAuthSource loadRiderAuthSource(long riderId) {
        return jdbcTemplate.query(
            """
                SELECT phone, current_openid, wechat_open_id, display_name, last_login_at
                FROM rider_profiles
                WHERE id = ?
                """,
            ps -> ps.setLong(1, riderId),
            rs -> {
                if (!rs.next()) {
                    throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "骑手不存在");
                }
                return new DispatchRiderAuthSource(
                    rs.getString("phone"),
                    rs.getString("current_openid"),
                    rs.getString("wechat_open_id"),
                    rs.getString("display_name"),
                    rs.getTimestamp("last_login_at")
                );
            }
        );
    }

    private record DispatchOrderContext(
        long customerId,
        LocalDate serveDate,
        long addressId,
        String mealPeriod,
        String addressLine
    ) {
    }

    private record DispatchNotificationContext(long customerId) {
    }

    private record DispatchRiderAuthSource(
        String phone,
        String currentOpenid,
        String wechatOpenId,
        String displayName,
        Timestamp lastLoginAt
    ) {
    }

    private record RememberedPendingOrderRow(
        long orderId,
        LocalDate serveDate,
        String mealPeriod,
        String areaCode
    ) {
    }

    private record RiderExceptionCountRow(String riderName, String areaCode, int exceptionCount) {
    }

    private record OrderNoteRow(long orderId, String noteType, String content) {
    }

    private record RememberedOrderContext(long customerId, long addressId, String rememberedAreaCode) {
    }

    private record RiderProgressRow(
        String riderName,
        String areaCode,
        long orderId,
        int sequenceNumber,
        String deliveryStatus,
        int quantity
    ) {
    }

    private record DispatchAreaOrderRow(
        long orderId,
        int sequenceNumber,
        String customerName,
        String customerPhone,
        String deliveryAddress,
        String deliveryStatus,
        String riderName,
        String userNote,
        String merchantRemark,
        String referenceImageUrl,
        String receiptUrl,
        String receiptNote,
        Timestamp deliveredAt,
        int quantity
    ) {
    }

    private record OrderNoteProjection(String userNote, String adminNote, boolean hasOrderNotes) {
    }

    private static final class DispatchNoteAccumulator {
        private final List<String> userNotes = new ArrayList<>();
        private final List<String> merchantNotes = new ArrayList<>();

        private void add(String noteType, String content) {
            String normalized = content == null ? "" : content.trim();
            if (normalized.isBlank() || "-".equals(normalized)) {
                return;
            }
            List<String> target = "MERCHANT".equals(noteType) ? merchantNotes : userNotes;
            if (!target.contains(normalized)) {
                target.add(normalized);
            }
        }

        private OrderNoteProjection toProjection() {
            return new OrderNoteProjection(
                String.join(" / ", userNotes),
                String.join(" / ", merchantNotes),
                true
            );
        }
    }

    private static final class RiderProgressAccumulator {
        private final String key;
        private final String riderName;
        private final String areaCode;
        private int totalCount;
        private int completedCount;
        private long currentOrderId;
        private int currentSequenceNumber;
        private long nextOrderId;
        private boolean currentCaptured;

        private RiderProgressAccumulator(String key, String riderName, String areaCode) {
            this.key = key;
            this.riderName = riderName;
            this.areaCode = areaCode;
        }

        private boolean matches(String otherKey) {
            return key.equals(otherKey);
        }

        private String key() {
            return key;
        }

        private void add(RiderProgressRow row) {
            int quantity = Math.max(row.quantity(), 1);
            totalCount += quantity;
            if ("DELIVERED".equals(row.deliveryStatus())) {
                completedCount += quantity;
                return;
            }
            if (!currentCaptured) {
                currentOrderId = row.orderId();
                currentSequenceNumber = row.sequenceNumber();
                currentCaptured = true;
                return;
            }
            if (nextOrderId == 0L) {
                nextOrderId = row.orderId();
            }
        }

        private DispatchRiderProgressResponse toResponse(int exceptionCount) {
            return new DispatchRiderProgressResponse(
                riderName,
                areaCode,
                completedCount,
                totalCount,
                currentOrderId,
                currentSequenceNumber,
                nextOrderId,
                Math.max(totalCount - completedCount, 0),
                exceptionCount
            );
        }
    }

    private record DispatchMatchResult(boolean matched, String riderName, String areaCode) {
    }
}
