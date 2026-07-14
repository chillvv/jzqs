package com.jzqs.app.dispatch.service.impl;

import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.dispatch.api.DispatchAreaOrderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchAreaOrdersReorderResponse;
import com.jzqs.app.dispatch.api.DispatchAreaRiderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchAutoAssignResponse;
import com.jzqs.app.dispatch.api.DispatchExceptionAreaConfirmResponse;
import com.jzqs.app.dispatch.api.DispatchOrderAreaMoveResponse;
import com.jzqs.app.dispatch.api.DispatchOrderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchOrderReorderItemRequest;
import com.jzqs.app.dispatch.api.DispatchReassignResultResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
class DispatchAssignmentModule {
    private static final int DISPATCH_EXCEPTION_RETENTION_DAYS = 30;
    private static final int DISPATCH_REASSIGNMENT_RETENTION_DAYS = 30;
    private static final String DEFAULT_OPERATOR = "SYSTEM";

    private final JdbcTemplate jdbcTemplate;
    private final DispatchBatchModule dispatchBatchModule;
    private final RealtimeAudienceModule realtimeAudienceModule;

    DispatchAssignmentModule(
        JdbcTemplate jdbcTemplate,
        DispatchBatchModule dispatchBatchModule,
        RealtimeAudienceModule realtimeAudienceModule
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatchBatchModule = dispatchBatchModule;
        this.realtimeAudienceModule = realtimeAudienceModule;
    }

    DispatchAutoAssignResponse autoAssignPendingOrders(String mealPeriod) {
        int assignedCount = autoAssignRememberedPendingOrders(mealPeriod);
        return new DispatchAutoAssignResponse(assignedCount, 0);
    }

    int ensureRememberedAssignments(String mealPeriod) {
        return autoAssignRememberedPendingOrders(mealPeriod);
    }

    BatchOperationResponse batchAssignPendingOrders(
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
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "订单编号不能为空");
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
                    Integer existing = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = ?",
                        Integer.class,
                        orderId
                    );
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
                    ex.getMessage() == null ? "批量处理失败" : ex.getMessage()
                ));
            }
        }
        publishDispatchEvent("dispatch.assignment.changed", normalizedAreaCode, defaultRiderName, successCount);
        return new BatchOperationResponse(successCount, failures.size(), failures);
    }

    DispatchOrderAssignResponse assignOrder(long orderId, String riderName, String areaCode) {
        dispatchOrder(orderId, riderName, areaCode, true);
        markDispatchExceptionResolved(orderId, riderName);
        publishDispatchEvent("dispatch.assignment.changed", areaCode, riderName, orderId);
        return new DispatchOrderAssignResponse(orderId, riderName, "DISPATCHED");
    }

    DispatchExceptionAreaConfirmResponse confirmExceptionArea(
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

    DispatchAreaRiderAssignResponse assignRiderToArea(String areaCode, String riderName, String mealPeriod) {
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

    DispatchAreaOrderAssignResponse assignRiderToAreaOrder(String areaCode, long orderId, String riderName) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        dispatchOrder(orderId, resolveAssignmentRiderName(normalizedAreaCode, riderName), normalizedAreaCode, true);
        publishDispatchEvent("dispatch.queue.changed", normalizedAreaCode, riderName, orderId);
        return new DispatchAreaOrderAssignResponse(normalizedAreaCode, orderId, "DISPATCHED");
    }

    DispatchAreaOrdersReorderResponse reorderAreaOrders(String areaCode, List<DispatchOrderReorderItemRequest> items) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        if (items == null || items.isEmpty()) {
            return new DispatchAreaOrdersReorderResponse(normalizedAreaCode, 0);
        }

        List<Long> orderIds = items.stream()
            .map(DispatchOrderReorderItemRequest::orderId)
            .filter(id -> id != null && id > 0)
            .toList();
        if (!orderIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(orderIds.size(), "?"));
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

    DispatchOrderAreaMoveResponse moveOrderToArea(String areaCode, long orderId, String targetAreaCode, String updatedBy) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        String normalizedTargetAreaCode = requireAreaCode(targetAreaCode);
        DispatchOrderContext orderContext = loadOrderContext(orderId);

        dispatchBatchModule.removeBatchItem(orderId);
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

    DispatchReassignResultResponse reassignDispatch(
        String reassignLevel,
        long targetId,
        String fromRiderName,
        String toRiderName,
        String toAreaCode,
        String serveDate,
        String mealPeriod,
        boolean syncDefaultBinding,
        String reason,
        String createdBy,
        AreaBindingUpdater areaBindingUpdater
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
                areaBindingUpdater.update(finalAreaCode, null, riderId, null, createdBy);
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

    private void dispatchOrder(long orderId, String riderName, String areaCode, boolean syncAddressBinding) {
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'DISPATCHING' WHERE id = ?", orderId);
        long riderProfileId = ensureRiderProfile(riderName, areaCode);
        DispatchOrderContext orderContext = loadOrderContext(orderId);
        int sequenceNumber = nextAreaSequence(
            areaCode,
            orderContext.serveDate(),
            orderContext.mealPeriod()
        );
        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = ?",
            Integer.class,
            orderId
        );
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
                riderName,
                riderProfileId,
                areaCode,
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
                        sequence_number
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                orderId,
                riderName,
                riderProfileId,
                areaCode,
                "DISPATCHING",
                sequenceNumber
            );
        }
        if (syncAddressBinding) {
            syncAddressBinding(orderId, riderProfileId, areaCode);
        }
        int finalSequenceNumber = dispatchBatchModule.ensureBatchItem(
            orderId,
            riderProfileId,
            areaCode,
            orderContext.serveDate(),
            orderContext.mealPeriod()
        );
        jdbcTemplate.update(
            "UPDATE dispatch_assignments SET sequence_number = ? WHERE meal_slot_order_id = ?",
            finalSequenceNumber,
            orderId
        );
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
                int sequenceNumber = nextAreaSequence(areaCode, order.serveDate(), order.mealPeriod());
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

    private String requireAreaCode(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择区域");
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
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "所选区域暂未绑定可派单骑手，请先指定骑手或只归区域");
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
        int existing = queryCount(
            "SELECT COUNT(*) FROM rider_address_bindings WHERE customer_id = " + customerId + " AND address_id = " + addressId
        );
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

    private void publishDispatchEvent(String eventType, String areaCode, String riderName, Object orderId) {
        realtimeAudienceModule.publishDispatchEvent(eventType, areaCode, riderName, orderId);
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

    private Long findRiderProfileIdByName(String riderName) {
        List<Long> ids = jdbcTemplate.query(
            "SELECT id FROM rider_profiles WHERE rider_name = ?",
            (rs, rowNum) -> rs.getLong("id"),
            riderName
        );
        return ids.isEmpty() ? null : ids.get(0);
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

    private String riderNameOrDefault(String riderName) {
        return riderName == null || riderName.isBlank() ? DEFAULT_OPERATOR : riderName.trim();
    }

    private String normalizeAddressFingerprint(String addressLine) {
        if (addressLine == null) {
            return "";
        }
        return addressLine.replace(" ", "").replace("-", "").replace("，", "").replace(",", "");
    }

    private int queryCount(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
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
        return key == null ? 0L : key.longValue();
    }

    @FunctionalInterface
    interface AreaBindingUpdater {
        void update(String areaCode, String keywords, Long defaultRiderId, Long backupRiderId, String updatedBy);
    }

    private record DispatchOrderContext(
        long customerId,
        LocalDate serveDate,
        long addressId,
        String mealPeriod,
        String addressLine
    ) {
    }

    private record RememberedPendingOrderRow(
        long orderId,
        LocalDate serveDate,
        String mealPeriod,
        String areaCode
    ) {
    }
}
