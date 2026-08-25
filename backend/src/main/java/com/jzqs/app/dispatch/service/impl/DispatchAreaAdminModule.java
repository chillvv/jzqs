package com.jzqs.app.dispatch.service.impl;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.api.DispatchAreaBindingRemoveResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingUpdateResultResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBlockingOrderResponse;
import com.jzqs.app.dispatch.api.DispatchAreaDeleteResponse;
import com.jzqs.app.dispatch.api.DispatchAreaRenameResponse;
import com.jzqs.app.order.MealPeriod;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class DispatchAreaAdminModule {
    private final JdbcTemplate jdbcTemplate;
    private final DispatchAssignmentModule dispatchAssignmentModule;

    DispatchAreaAdminModule(JdbcTemplate jdbcTemplate, DispatchAssignmentModule dispatchAssignmentModule) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatchAssignmentModule = dispatchAssignmentModule;
    }

    DispatchAreaBindingUpdateResultResponse updateAreaBinding(
        MealPeriod mealPeriod,
        String areaCode,
        String keywords,
        Long defaultRiderId,
        Long backupRiderId,
        String updatedBy
    ) {
        String normalizedAreaCode = areaCode == null ? null : areaCode.trim();
        String resolvedMealPeriod = (mealPeriod == null ? MealPeriod.DINNER : mealPeriod).name();
        // 区域配置只负责「归区域」的记忆（area_code + keywords），不绑定默认骑手自动派单。
        // 骑手跨区互斥在手动分配骑手（assignRiderToArea）时处理。
        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_area_bindings WHERE area_code = ? AND meal_period = ?",
            Integer.class,
            normalizedAreaCode,
            resolvedMealPeriod
        );
        String status = "UPDATED";
        if (existing != null && existing > 0) {
            jdbcTemplate.update(
                """
                    UPDATE dispatch_area_bindings
                    SET keywords = COALESCE(?, keywords),
                        default_rider_profile_id = NULL,
                        backup_rider_profile_id = NULL,
                        updated_by = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE area_code = ? AND meal_period = ?
                    """,
                keywords,
                updatedBy,
                normalizedAreaCode,
                resolvedMealPeriod
            );
        } else {
            jdbcTemplate.update(
                """
                    INSERT INTO dispatch_area_bindings (
                        area_code,
                        meal_period,
                        keywords,
                        default_rider_profile_id,
                        backup_rider_profile_id,
                        updated_by,
                        updated_at
                    )
                    VALUES (?, ?, ?, NULL, NULL, ?, CURRENT_TIMESTAMP)
                    """,
                normalizedAreaCode,
                resolvedMealPeriod,
                keywords,
                updatedBy
            );
            status = "CREATED";
        }
        // 注意：区域配置不再绑定「默认骑手」自动派单。
        // 订单进入区域后必须由操作员手动分配给具体骑手（分配了才是他的，不分配就不归属任何人）。
        // 这里仅做区域基础信息（area_code / keywords）的记忆与骑手跨区互斥校验。
        return new DispatchAreaBindingUpdateResultResponse(
            normalizedAreaCode,
            keywords,
            null,
            null,
            status
        );
    }

    /**
     * 将骑手从其它区域中释放出来，实现双向同步（按餐段隔离）：
     * 1. 默认骑手从其它区域的 default_rider_profile_id 中移除，并清理该骑手的 default_area_code；
     * 2. 备用骑手从其它区域的 backup_rider_profile_id 中移除（不影响其负责区域）。
     */
    private void releaseRiderFromOtherAreas(String areaCode, String mealPeriod, Long defaultRiderId, Long backupRiderId) {
        String targetArea = areaCode == null ? "" : areaCode;
        String targetPeriod = mealPeriod == null ? "" : mealPeriod;
        if (defaultRiderId != null && defaultRiderId > 0) {
            jdbcTemplate.update(
                """
                    UPDATE dispatch_area_bindings
                    SET default_rider_profile_id = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE default_rider_profile_id = ?
                      AND area_code <> ?
                      AND meal_period = ?
                    """,
                defaultRiderId,
                targetArea,
                targetPeriod
            );
            jdbcTemplate.update(
                "UPDATE rider_profiles SET default_area_code = NULL WHERE id = ? AND default_area_code <> ? AND meal_period = ?",
                defaultRiderId,
                targetArea,
                targetPeriod
            );
        }
        if (backupRiderId != null && backupRiderId > 0) {
            jdbcTemplate.update(
                """
                    UPDATE dispatch_area_bindings
                    SET backup_rider_profile_id = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE backup_rider_profile_id = ?
                      AND area_code <> ?
                      AND meal_period = ?
                    """,
                backupRiderId,
                targetArea,
                targetPeriod
            );
        }
    }

    DispatchAreaBindingRemoveResponse removeAreaBinding(MealPeriod mealPeriod, String areaCode, long riderId) {
        String resolvedMealPeriod = (mealPeriod == null ? MealPeriod.DINNER : mealPeriod).name();
        jdbcTemplate.update(
            "DELETE FROM dispatch_area_bindings WHERE area_code = ? AND meal_period = ? AND default_rider_profile_id = ?",
            areaCode,
            resolvedMealPeriod,
            riderId
        );
        jdbcTemplate.update(
            "UPDATE rider_profiles SET default_area_code = NULL WHERE id = ? AND default_area_code = ? AND meal_period = ?",
            riderId,
            areaCode,
            resolvedMealPeriod
        );
        return new DispatchAreaBindingRemoveResponse(areaCode, riderId, "REMOVED");
    }

    DispatchAreaRenameResponse renameArea(String areaCode, String newAreaCode) {
        String trimmed = newAreaCode.trim();
        if (trimmed.isEmpty() || trimmed.equals(areaCode)) {
            throw new IllegalArgumentException("invalid area name");
        }
        jdbcTemplate.update(
            "UPDATE dispatch_area_bindings SET area_code = ? WHERE area_code = ?",
            trimmed,
            areaCode
        );
        jdbcTemplate.update(
            "UPDATE dispatch_assignments SET area_code = ? WHERE area_code = ?",
            trimmed,
            areaCode
        );
        jdbcTemplate.update(
            "UPDATE rider_profiles SET default_area_code = ? WHERE default_area_code = ?",
            trimmed,
            areaCode
        );
        return new DispatchAreaRenameResponse(areaCode, trimmed, "RENAMED");
    }

    DispatchAreaDeleteResponse deleteArea(String areaCode) {
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
}
