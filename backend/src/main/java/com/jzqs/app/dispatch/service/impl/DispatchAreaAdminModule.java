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

    DispatchAreaAdminModule(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    DispatchAreaBindingUpdateResultResponse updateAreaBinding(
        String areaCode,
        String keywords,
        Long defaultRiderId,
        Long backupRiderId,
        String updatedBy
    ) {
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

    DispatchAreaBindingRemoveResponse removeAreaBinding(String areaCode, long riderId) {
        jdbcTemplate.update(
            "DELETE FROM dispatch_area_bindings WHERE area_code = ? AND default_rider_profile_id = ?",
            areaCode,
            riderId
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
