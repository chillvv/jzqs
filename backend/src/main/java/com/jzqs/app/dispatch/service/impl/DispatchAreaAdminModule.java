package com.jzqs.app.dispatch.service.impl;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.api.DispatchAreaBindingRemoveResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingUpdateResultResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBlockingOrderResponse;
import com.jzqs.app.dispatch.api.DispatchAreaDeleteResponse;
import com.jzqs.app.dispatch.api.DispatchAreaRenameResponse;
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
        String areaCode,
        String keywords,
        Long defaultRiderId,
        Long backupRiderId,
        String updatedBy
    ) {
        String normalizedAreaCode = areaCode == null ? null : areaCode.trim();

        // 双向同步第一步：把即将绑定的默认/备用骑手从其它区域释放，
        // 避免同一个骑手同时挂在多个区域的 default/backup 上。
        releaseRiderFromOtherAreas(normalizedAreaCode, defaultRiderId, backupRiderId);

        // 双向同步第二步：把本区域「原来的默认骑手」从骑手档案中释放
        // （若其 default_area_code 仍指向本区域则置空），保证区域默认骑手与骑手归属区域一一对应。
        if (defaultRiderId != null && defaultRiderId > 0) {
            jdbcTemplate.update(
                """
                    UPDATE rider_profiles
                    SET default_area_code = NULL
                    WHERE default_area_code = ?
                      AND id <> ?
                    """,
                normalizedAreaCode,
                defaultRiderId
            );
        } else {
            jdbcTemplate.update(
                """
                    UPDATE rider_profiles
                    SET default_area_code = NULL
                    WHERE default_area_code = ?
                    """,
                normalizedAreaCode
            );
        }

        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_area_bindings WHERE area_code = ?",
            Integer.class,
            normalizedAreaCode
        );
        String status = "UPDATED";
        if (existing != null && existing > 0) {
            jdbcTemplate.update(
                """
                    UPDATE dispatch_area_bindings
                    SET keywords = COALESCE(?, keywords),
                        default_rider_profile_id = ?,
                        backup_rider_profile_id = ?,
                        active = 1,
                        updated_by = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE area_code = ?
                    """,
                keywords,
                defaultRiderId,
                backupRiderId,
                updatedBy,
                normalizedAreaCode
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
                normalizedAreaCode,
                keywords,
                defaultRiderId,
                backupRiderId,
                updatedBy
            );
            status = "CREATED";
        }

        // 双向同步第三步：把新默认骑手的归属区域回写为其档案 default_area_code，
        // 与「骑手管理」列表展示保持一致。
        if (defaultRiderId != null && defaultRiderId > 0 && normalizedAreaCode != null) {
            jdbcTemplate.update(
                """
                    UPDATE rider_profiles
                    SET default_area_code = ?
                    WHERE id = ?
                    """,
                normalizedAreaCode,
                defaultRiderId
            );
        }

        return new DispatchAreaBindingUpdateResultResponse(
            normalizedAreaCode,
            keywords,
            defaultRiderId,
            backupRiderId,
            status
        );
    }

    /**
     * 将骑手从其它区域中释放出来，实现双向同步：
     * 1. 默认骑手从其它区域的 default_rider_profile_id 中移除，并清理该骑手的 default_area_code；
     * 2. 备用骑手从其它区域的 backup_rider_profile_id 中移除（不影响其负责区域）。
     */
    private void releaseRiderFromOtherAreas(String areaCode, Long defaultRiderId, Long backupRiderId) {
        String targetArea = areaCode == null ? "" : areaCode;
        if (defaultRiderId != null && defaultRiderId > 0) {
            jdbcTemplate.update(
                """
                    UPDATE dispatch_area_bindings
                    SET default_rider_profile_id = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE default_rider_profile_id = ?
                      AND area_code <> ?
                    """,
                defaultRiderId,
                targetArea
            );
            jdbcTemplate.update(
                "UPDATE rider_profiles SET default_area_code = NULL WHERE id = ? AND default_area_code <> ?",
                defaultRiderId,
                targetArea
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
                    """,
                backupRiderId,
                targetArea
            );
        }
    }

    DispatchAreaBindingRemoveResponse removeAreaBinding(String areaCode, long riderId) {
        jdbcTemplate.update(
            "DELETE FROM dispatch_area_bindings WHERE area_code = ? AND default_rider_profile_id = ?",
            areaCode,
            riderId
        );
        jdbcTemplate.update(
            "UPDATE rider_profiles SET default_area_code = NULL WHERE id = ? AND default_area_code = ?",
            riderId,
            areaCode
        );
        return new DispatchAreaBindingRemoveResponse(areaCode, riderId, "REMOVED");
    }

    DispatchAreaRenameResponse renameArea(String areaCode, String newAreaCode) {
        String trimmed = newAreaCode.trim();
        if (trimmed.isEmpty() || trimmed.equals(areaCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "区域名称不能为空，且不能与原名称相同");
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
        jdbcTemplate.update(
            "UPDATE rider_address_bindings SET area_code = ? WHERE area_code = ?",
            trimmed,
            areaCode
        );
        jdbcTemplate.update(
            "UPDATE dispatch_batches SET area_code = ? WHERE area_code = ?",
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
                  -- 订单中心已取消/已退款的订单不视为配送中,不应阻塞区域删除
                  AND mso.status NOT IN ('CANCELLED', 'REFUNDED')
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
        // 解绑骑手档案的归属区域（软删/物理删都执行，保证区域与骑手不再关联）。
        jdbcTemplate.update(
            "UPDATE rider_profiles SET default_area_code = NULL WHERE default_area_code = ?",
            areaCode
        );
        // 删除地址-骑手绑定（软删/物理删都执行，保证已删区域不再参与自动归区）。
        jdbcTemplate.update(
            "DELETE FROM rider_address_bindings WHERE area_code = ?",
            areaCode
        );
        // 分流：有历史订单引用 -> 软删除（停用），保留区域行，历史订单 area_code 不产生孤儿；
        //       无历史订单 -> 物理删除（对应「创建错想删」的场景）。
        Integer assignmentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_assignments WHERE area_code = ?",
            Integer.class,
            areaCode
        );
        if (assignmentCount != null && assignmentCount > 0) {
            jdbcTemplate.update(
                """
                    UPDATE dispatch_area_bindings
                    SET active = 0,
                        default_rider_profile_id = NULL,
                        backup_rider_profile_id = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE area_code = ?
                    """,
                areaCode
            );
            return new DispatchAreaDeleteResponse(areaCode, "DEACTIVATED");
        }
        jdbcTemplate.update(
            "DELETE FROM dispatch_area_bindings WHERE area_code = ?",
            areaCode
        );
        return new DispatchAreaDeleteResponse(areaCode, "DELETED");
    }
}
