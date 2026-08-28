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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
class DispatchAssignmentModule {
    private static final Logger log = LoggerFactory.getLogger(DispatchAssignmentModule.class);
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
        int successCount = 0;
        List<BatchOperationResponse.FailureItem> failures = new ArrayList<>();
        for (Long orderId : orderIds) {
            try {
                if (orderId == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "订单编号不能为空");
                }
                log.info("批量派单处理订单: orderId={} area={} operator={}", orderId, normalizedAreaCode, updatedBy);
                DispatchOrderContext orderContext = loadOrderContext(orderId);
                String defaultRiderName = resolveDefaultRiderName(normalizedAreaCode);
                if (defaultRiderName != null) {
                    dispatchOrder(orderId, defaultRiderName, normalizedAreaCode, true);
                } else {
                    // 归区前校验订单真实状态:已取消/已退款的订单不允许写入派单记录,
                    // 与 dispatchOrder 的状态前置校验同口径,从源头杜绝「订单中心已退款但骑手进度残留」。
                    String orderStatus = loadOrderStatusForUpdate(orderId);
                    if (!"PENDING_DISPATCH".equals(orderStatus)) {
                        throw new BusinessException(
                            ErrorCode.ORDER_STATUS_INVALID,
                            "订单状态已变更（" + (orderStatus == null ? "不存在" : orderStatus) + "），无法归区，请刷新后重试"
                        );
                    }
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
                log.warn("批量派单单个订单失败: orderId={} area={} 原因={}", orderId, normalizedAreaCode, ex.getMessage());
                failures.add(new BatchOperationResponse.FailureItem(
                    orderId,
                    "BATCH_ASSIGN_FAILED",
                    ex.getMessage() == null ? "批量处理失败" : ex.getMessage()
                ));
            }
        }
        log.info("批量派单完成: area={} 成功={} 失败={}", normalizedAreaCode, successCount, failures.size());
        publishDispatchEvent("dispatch.assignment.changed", normalizedAreaCode, null, successCount);
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

        // 跨区互斥：骑手通过 rider_profiles.default_area_code 归属唯一区域，
        // 已属于其他区域则不允许跨区把该区域订单全部分配给它。
        Long riderProfileId = findRiderProfileIdByName(riderName);
        if (riderProfileId != null) {
            Integer occupiedElsewhere = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*) FROM rider_profiles
                    WHERE id = ?
                      AND default_area_code IS NOT NULL
                      AND default_area_code <> ?
                    """,
                Integer.class,
                riderProfileId,
                normalizedAreaCode
            );
            if (occupiedElsewhere != null && occupiedElsewhere > 0) {
                log.warn("拦截跨区指派: rider={} 已归属其他区域，目标区域={}, 请求餐段={}", riderName, normalizedAreaCode, finalMealPeriod);
                throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "骑手「" + riderName + "」已归属其他区域，不能跨区分配。请在本区域骑手范围内选择。"
                );
            }
        }

        // 更换骑手：只转移该区域「今天及以后」且「未完成」的订单（不分午餐/晚餐），
        // 之前日期（已送达/已取消）的历史单保持原骑手不动。
        // 关键：按送餐日期 serve_date >= 今天过滤，避免把前几天的单子也捞进来；
        // 同时校验 meal_slot_orders.status（订单真实状态），防止「已送达但分配记录未同步」的脏单触发派单报错。
        List<Long> orderIds = jdbcTemplate.query(
            """
                SELECT da.meal_slot_order_id
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE da.area_code = ?
                  AND da.status IN ('PENDING', 'AREA_ASSIGNED', 'DISPATCHING')
                  AND mso.status IN ('PENDING_DISPATCH', 'DISPATCHING')
                  AND doo.serve_date >= CURRENT_DATE
                ORDER BY da.sequence_number, da.id
                """,
            (rs, rowNum) -> rs.getLong("meal_slot_order_id"),
            normalizedAreaCode
        );

        int successCount = 0;
        for (Long orderId : orderIds) {
            dispatchOrder(orderId, resolveAssignmentRiderName(normalizedAreaCode, riderName), normalizedAreaCode, true);
            successCount++;
        }
        // 派单后重新解析骑手档案（若骑手此前未建档，dispatchOrder 内部已创建）。
        Long resolvedRiderProfileId = findRiderProfileIdByName(riderName);
        if (resolvedRiderProfileId != null) {
            // 双向同步：释放本区域原默认骑手 + 将新骑手绑定为本区域当前骑手，
            // 保证「区域 ↔ 骑手」唯一对应，且之后进入该区域的订单自动归此骑手。
            bindRiderToArea(normalizedAreaCode, resolvedRiderProfileId);
        }
        publishDispatchEvent("dispatch.queue.changed", normalizedAreaCode, riderName, null);
        return new DispatchAreaRiderAssignResponse(
            normalizedAreaCode,
            successCount,
            finalMealPeriod == null ? "" : finalMealPeriod
        );
    }

    /**
     * 将骑手绑定为某区域的当前骑手（双向同步）：
     * 1) 释放本区域原默认骑手的归属区域；
     * 2) 释放新骑手在其他区域的默认绑定；
     * 3) 写入本区域的 default_rider_profile_id；
     * 4) 回写新骑手的 default_area_code。
     */
    private void bindRiderToArea(String areaCode, long riderProfileId) {
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET default_area_code = NULL
                WHERE default_area_code = ?
                  AND id <> ?
                """,
            areaCode,
            riderProfileId
        );
        jdbcTemplate.update(
            """
                UPDATE dispatch_area_bindings
                SET default_rider_profile_id = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE default_rider_profile_id = ?
                  AND area_code <> ?
                """,
            riderProfileId,
            areaCode
        );
        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_area_bindings WHERE area_code = ?",
            Integer.class,
            areaCode
        );
        if (existing != null && existing > 0) {
            jdbcTemplate.update(
                """
                    UPDATE dispatch_area_bindings
                    SET default_rider_profile_id = ?,
                        backup_rider_profile_id = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE area_code = ?
                    """,
                riderProfileId,
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
                    ) VALUES (?, NULL, ?, NULL, ?, CURRENT_TIMESTAMP)
                    """,
                areaCode,
                riderProfileId,
                DEFAULT_OPERATOR
            );
        }
        jdbcTemplate.update(
            "UPDATE rider_profiles SET default_area_code = ? WHERE id = ?",
            areaCode,
            riderProfileId
        );
        log.info("区域-骑手绑定双向同步完成: area={} riderProfileId={}（已释放其他区域绑定并回写 default_area_code）",
            areaCode, riderProfileId);
    }

    DispatchAreaOrderAssignResponse assignRiderToAreaOrder(String areaCode, long orderId, String riderName) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        // 收敛：订单只能在本区域骑手之间切换，禁止把已属于其他区域的骑手跨区拉入。
        Long riderProfileId = findRiderProfileIdByName(riderName);
        if (riderProfileId != null) {
            // 跨区互斥：骑手通过 rider_profiles.default_area_code 归属唯一区域，
            // 已属于其他区域则不允许把本区域订单跨区分配给该骑手。
            Integer occupiedElsewhere = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*) FROM rider_profiles
                    WHERE id = ?
                      AND default_area_code IS NOT NULL
                      AND default_area_code <> ?
                    """,
                Integer.class,
                riderProfileId,
                normalizedAreaCode
            );
            if (occupiedElsewhere != null && occupiedElsewhere > 0) {
                log.warn("拦截跨区指派(单订单): rider={} 已归属其他区域，目标区域={}, orderId={}", riderName, normalizedAreaCode, orderId);
                throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "骑手「" + riderName + "」已归属其他区域，不能跨区分配。请在本区域骑手范围内选择。"
                );
            }
        }
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
        if (orderIds.isEmpty()) {
            return new DispatchAreaOrdersReorderResponse(normalizedAreaCode, 0);
        }

        // 收集本次涉及的所有批次。腾位必须覆盖整个批次（含骑手端手动调整过、manually_adjusted=TRUE 的行），
        // 否则写入目标序号时会撞 uk_dispatch_batch_items_batch_sequence(batch_id, current_sequence) 唯一键，
        // 报 DuplicateKeyException（日志可见 Duplicate entry '<batchId>-<seq>'）。
        String orderPlaceholders = String.join(",", Collections.nCopies(orderIds.size(), "?"));
        List<Long> batchIds = jdbcTemplate.query(
            "SELECT DISTINCT batch_id FROM dispatch_batch_items WHERE meal_slot_order_id IN (" + orderPlaceholders + ")",
            (rs, rowNum) -> rs.getLong("batch_id"),
            orderIds.toArray()
        );

        // 1) 把涉及批次内的所有行序号整体抬升，为 1..N 腾出连续空间。
        //    不再按 manually_adjusted 过滤：管理员调整区域顺序是权威操作，必须能覆盖骑手端的手动调整。
        if (!batchIds.isEmpty()) {
            String batchPlaceholders = String.join(",", Collections.nCopies(batchIds.size(), "?"));
            jdbcTemplate.update(
                "UPDATE dispatch_batch_items SET current_sequence = current_sequence + 100000 WHERE batch_id IN (" + batchPlaceholders + ")",
                batchIds.toArray()
            );
        }

        // 2) 逐单写入目标序号（管理员调整覆盖骑手端手动调整，不再排除 manually_adjusted 行）。
        int updatedCount = 0;
        for (DispatchOrderReorderItemRequest item : items) {
            if (item.orderId() == null || item.orderId() <= 0) {
                continue;
            }
            jdbcTemplate.update(
                "UPDATE dispatch_assignments SET sequence_number = ? WHERE meal_slot_order_id = ? AND area_code = ?",
                item.sequenceNumber(),
                item.orderId(),
                normalizedAreaCode
            );
            int affected = jdbcTemplate.update(
                "UPDATE dispatch_batch_items SET current_sequence = ? WHERE meal_slot_order_id = ?",
                item.sequenceNumber(),
                item.orderId()
            );
            if (affected > 0) {
                updatedCount++;
            }
        }

        // 3) 把批次内未出现在本次 items 中的订单（例如已送达、或漏传的订单）按原相对顺序
        //    压缩重编号到 items 之后，保持批次序号连续，避免出现 100001 之类的大编号残留。
        if (!batchIds.isEmpty()) {
            String batchPlaceholders = String.join(",", Collections.nCopies(batchIds.size(), "?"));
            List<Object> tailArgs = new ArrayList<>(batchIds.size() + orderIds.size());
            tailArgs.addAll(batchIds);
            tailArgs.addAll(orderIds);
            List<Long> tailOrderIds = jdbcTemplate.query(
                "SELECT meal_slot_order_id FROM dispatch_batch_items WHERE batch_id IN ("
                    + batchPlaceholders + ") AND meal_slot_order_id NOT IN (" + orderPlaceholders + ") ORDER BY current_sequence, id",
                (rs, rowNum) -> rs.getLong("meal_slot_order_id"),
                tailArgs.toArray()
            );
            int nextSequence = orderIds.size() + 1;
            for (Long tailOrderId : tailOrderIds) {
                jdbcTemplate.update(
                    "UPDATE dispatch_batch_items SET current_sequence = ? WHERE meal_slot_order_id = ?",
                    nextSequence++,
                    tailOrderId
                );
            }
        }
        publishDispatchEvent("dispatch.queue.changed", normalizedAreaCode, null, items.get(0).orderId());
        return new DispatchAreaOrdersReorderResponse(normalizedAreaCode, updatedCount);
    }

    DispatchOrderAreaMoveResponse moveOrderToArea(String areaCode, long orderId, String targetAreaCode, String updatedBy) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        String normalizedTargetAreaCode = requireAreaCode(targetAreaCode);
        DispatchOrderContext orderContext = loadOrderContext(orderId);

        // 防御：已结束（已送达/已取消）的订单不允许移区，避免把完成订单重置回待派单。
        String currentStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM meal_slot_orders WHERE id = ?",
            String.class,
            orderId
        );
        if ("DELIVERED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单已结束（已送达/已取消），无法移区");
        }

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
        // 区域一旦绑定了骑手，移入的订单必须立即归属于该区域骑手，
        // 不允许出现「仅归区、未派单」(rider_name 为空) 的中间态。
        String targetDefaultRider = resolveDefaultRiderName(normalizedTargetAreaCode);
        if (targetDefaultRider != null) {
            dispatchOrder(orderId, targetDefaultRider, normalizedTargetAreaCode, true);
            publishDispatchEvent("dispatch.queue.changed", normalizedTargetAreaCode, targetDefaultRider, orderId);
        }
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
        // 状态前置校验：仅允许"待派单/派送中"的订单被派单，阻止已送达订单被非法回退为派送中
        int statusUpdated = jdbcTemplate.update(
            "UPDATE meal_slot_orders SET status = 'DISPATCHING' WHERE id = ? AND status IN ('PENDING_DISPATCH', 'DISPATCHING')",
            orderId
        );
        if (statusUpdated == 0) {
            String currentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM meal_slot_orders WHERE id = ?",
                String.class,
                orderId
            );
            log.warn("派单被拒绝: orderId={} rider={} area={} 订单状态={} 非 PENDING_DISPATCH/DISPATCHING",
                orderId, riderName, areaCode, currentStatus);
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单状态已变更，无法派单，请刷新后重试");
        }
        DispatchOrderContext orderContext = loadOrderContext(orderId);
        long riderProfileId = ensureRiderProfile(riderName, areaCode);
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
        log.info("订单派单成功: orderId={} rider={} riderProfileId={} area={} seq={}",
            orderId, riderName, riderProfileId, areaCode, finalSequenceNumber);
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
                JOIN (
                    SELECT
                        customer_id,
                        address_id,
                        area_code,
                        ROW_NUMBER() OVER (
                            PARTITION BY customer_id, address_id
                            ORDER BY
                                CASE WHEN meal_period <=> ? THEN 0 ELSE 1 END,
                                id DESC
                        ) AS rn
                    FROM rider_address_bindings
                ) rab
                    ON rab.customer_id = doo.customer_id
                   AND rab.address_id = mso.address_id
                   AND rab.rn = 1
                JOIN dispatch_area_bindings dab
                    ON dab.area_code = rab.area_code
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
            String targetRiderName = defaultRiderName;
            // 区域没有配置默认骑手时，跟随该区域「当前已有骑手」：
            // 一旦区域分配过骑手，后续进入的订单（含记忆归区）全部归属到同一骑手，
            // 避免出现「同区域一部分有骑手、一部分没有」的中间态。
            if (targetRiderName == null) {
                targetRiderName = resolveAreaCurrentRiderName(areaCode, order.mealPeriod(), order.serveDate());
            }
            if (targetRiderName != null) {
                dispatchOrder(orderId, targetRiderName, areaCode, true);
            } else {
                // 区域尚未分配过任何骑手：仅归区、骑手留空（待分配骑手），这是合法状态。
                // 归区前仍校验订单真实状态:已取消/已退款的订单不写入派单,避免骑手进度残留。
                String orderStatus = loadOrderStatusForUpdate(orderId);
                if (!"PENDING_DISPATCH".equals(orderStatus)) {
                    log.warn("自动归区跳过订单: orderId={} 状态={} 已取消/退款或不存在", orderId, orderStatus);
                    continue;
                }
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
        log.info("自动派单(记忆归区)完成: mealPeriod={} 处理订单数={}", finalMealPeriod, assignedCount);
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

    /**
     * 以 FOR UPDATE 行锁读取订单当前状态。
     * 用于归区/派单写入前的前置校验:确保订单仍处于 PENDING_DISPATCH,
     * 从源头阻止「订单中心已取消/已退款但派单记录仍被写入」的残留。
     */
    private String loadOrderStatusForUpdate(long orderId) {
        return jdbcTemplate.query(
            "SELECT status FROM meal_slot_orders WHERE id = ? FOR UPDATE",
            ps -> ps.setLong(1, orderId),
            rs -> rs.next() ? rs.getString("status") : null
        );
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
        String mealPeriod = normalizedMealPeriod(orderContext.mealPeriod());
        syncAddressBindingForArea(orderId, areaCode, mealPeriod, updatedBy, updatedReason);
    }

    private void syncAddressBindingForArea(
        long orderId,
        String areaCode,
        String mealPeriod,
        String updatedBy,
        String updatedReason
    ) {
        DispatchOrderContext orderContext = loadOrderContext(orderId);
        long customerId = orderContext.customerId();
        long addressId = orderContext.addressId();
        String addressLine = orderContext.addressLine();
        String fingerprint = normalizeAddressFingerprint(addressLine);
        Long riderProfileId = resolveDefaultRiderProfileId(areaCode);
        int existing = queryCount(
            "SELECT COUNT(*) FROM rider_address_bindings WHERE customer_id = ? AND address_id = ? AND (meal_period <=> ?)",
            customerId,
            addressId,
            mealPeriod
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
                    WHERE customer_id = ? AND address_id = ? AND (meal_period <=> ?)
                    """,
                fingerprint,
                areaCode,
                riderProfileId,
                updatedReason,
                customerId,
                addressId,
                mealPeriod
            );
            log.info("地址绑定刷新: customer={} address={} mealPeriod={} area={} riderProfileId={} reason={}",
                customerId, addressId, mealPeriod, areaCode, riderProfileId, updatedReason);
            return;
        }
        jdbcTemplate.update(
            """
                INSERT INTO rider_address_bindings (
                    customer_id,
                    address_id,
                    meal_period,
                    address_fingerprint,
                    area_code,
                    rider_profile_id,
                    manually_confirmed,
                    updated_reason,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, CURRENT_TIMESTAMP)
                """,
            customerId,
            addressId,
            mealPeriod,
            fingerprint,
            areaCode,
            riderProfileId,
            updatedReason
        );
        log.info("地址绑定新建: customer={} address={} mealPeriod={} area={} riderProfileId={} reason={}",
            customerId, addressId, mealPeriod, areaCode, riderProfileId, updatedReason);
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

    /**
     * 取该区域当前已有的骑手（从当天该餐段已有的 dispatch_assignments 中取一个非空的骑手名）。
     * 用于「后续订单跟随区域当前骑手」：一旦区域分配过骑手，新进订单全部归属到同一人。
     */
    private String resolveAreaCurrentRiderName(String areaCode, String mealPeriod, LocalDate serveDate) {
        List<String> riderNames = jdbcTemplate.query(
            """
                SELECT da.rider_name
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE da.area_code = ?
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                  AND da.rider_name IS NOT NULL
                  AND da.rider_name <> ''
                LIMIT 1
                """,
            (rs, rowNum) -> rs.getString("rider_name"),
            areaCode,
            serveDate,
            mealPeriod,
            mealPeriod
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
            """
                INSERT INTO rider_profiles (rider_name, employment_status, default_area_code, auth_status)
                VALUES (?, ?, ?, ?)
                """,
            riderName,
            "ACTIVE",
            areaCode,
            "ACTIVE"
        );
    }

    private void syncAddressBinding(long orderId, long riderProfileId, String areaCode) {
        DispatchOrderContext orderRow = loadOrderContext(orderId);
        String mealPeriod = normalizedMealPeriod(orderRow.mealPeriod());
        long customerId = orderRow.customerId();
        long addressId = orderRow.addressId();
        String addressLine = orderRow.addressLine();
        String fingerprint = normalizeAddressFingerprint(addressLine);
        int existing = queryCount(
            "SELECT COUNT(*) FROM rider_address_bindings WHERE customer_id = ? AND address_id = ? AND (meal_period <=> ?)",
            customerId,
            addressId,
            mealPeriod
        );
        if (existing > 0) {
            jdbcTemplate.update(
                "UPDATE rider_address_bindings SET address_fingerprint = ?, area_code = ?, rider_profile_id = ?, manually_confirmed = TRUE, updated_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE customer_id = ? AND address_id = ? AND (meal_period <=> ?)",
                fingerprint,
                areaCode,
                riderProfileId,
                "AREA_CONFIRMED",
                customerId,
                addressId,
                mealPeriod
            );
            log.info("派单同步地址绑定(UPDATE): customer={} address={} area={} riderProfileId={}",
                customerId, addressId, areaCode, riderProfileId);
            return;
        }
        jdbcTemplate.update(
            "INSERT INTO rider_address_bindings (customer_id, address_id, meal_period, address_fingerprint, area_code, rider_profile_id, manually_confirmed, updated_reason, updated_at) VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, CURRENT_TIMESTAMP)",
            customerId,
            addressId,
            mealPeriod,
            fingerprint,
            areaCode,
            riderProfileId,
            "AREA_CONFIRMED"
        );
        log.info("派单同步地址绑定(INSERT): customer={} address={} area={} riderProfileId={}",
            customerId, addressId, areaCode, riderProfileId);
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
        // 骑手唯一：一个姓名对应唯一档案（已去除 meal_period 拆分）。
        List<Long> ids = jdbcTemplate.query(
            """
                SELECT id FROM rider_profiles
                WHERE rider_name = ?
                ORDER BY id
                LIMIT 1
                """,
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
