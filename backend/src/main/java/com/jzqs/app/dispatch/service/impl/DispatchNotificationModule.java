package com.jzqs.app.dispatch.service.impl;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.api.DispatchNotificationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class DispatchNotificationModule {
    private final JdbcTemplate jdbcTemplate;

    DispatchNotificationModule(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    DispatchNotificationResponse notifyCustomer(long dispatchId) {
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_assignments WHERE id = ?",
            Integer.class,
            dispatchId
        );
        if (exists == null || exists == 0) {
            return new DispatchNotificationResponse(dispatchId, "NOT_FOUND");
        }
        loadNotificationCustomerId(dispatchId);
        return new DispatchNotificationResponse(dispatchId, "SKIPPED");
    }

    private long loadNotificationCustomerId(long dispatchId) {
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
                return rs.getLong("customer_id");
            }
        );
    }
}
