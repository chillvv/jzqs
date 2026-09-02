package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.order.api.DeliveryReleasePendingItem;
import com.jzqs.app.order.api.DeliveryReleaseResult;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台"送达状态立即释放"支持：
 * 订单送达后默认按餐期释放时间（午餐11:30 / 晚餐17:00）才对用户可见并发送订阅消息；
 * 遇到用户要求提前送达等特殊情况时，后台可对个别订单立即释放——状态即刻变为"已送达"、
 * 回执图片立即可见，并立即发送取餐提醒订阅消息。
 */
@Component
public class DeliveryReleaseSupport {
    private final JdbcTemplate jdbcTemplate;
    private final DeliverySubscriptionModule deliverySubscriptionModule;
    private final RealtimeAudienceModule realtimeAudienceModule;

    public DeliveryReleaseSupport(
        JdbcTemplate jdbcTemplate,
        DeliverySubscriptionModule deliverySubscriptionModule,
        RealtimeAudienceModule realtimeAudienceModule
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.deliverySubscriptionModule = deliverySubscriptionModule;
        this.realtimeAudienceModule = realtimeAudienceModule;
    }

    /** 列出"已送达但被时间拦截、等待餐期释放"的订单 */
    public List<DeliveryReleasePendingItem> pendingReleaseOrders(String serveDate, String mealPeriod) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    mso.id AS order_id,
                    doo.serve_date AS serve_date,
                    mso.meal_period AS meal_period,
                    COALESCE(mso.quantity, 1) AS quantity,
                    c.name AS customer_name,
                    COALESCE(c.phone, '') AS customer_phone,
                    CASE WHEN ca.door_number IS NOT NULL AND ca.door_number <> ''
                         THEN CONCAT(ca.address_line, ' ', ca.door_number)
                         ELSE ca.address_line END AS delivery_address,
                    dr.delivered_at AS delivered_at,
                    COALESCE(cds.status,
                        CASE WHEN EXISTS (
                            SELECT 1 FROM customer_delivery_subscriptions cds2
                            WHERE cds2.customer_id = doo.customer_id
                              AND cds2.status IN ('AUTHORIZED', 'FAILED')
                        ) THEN '' ELSE 'NO_CONSENT' END
                    ) AS subscription_status
                FROM meal_slot_orders mso
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                JOIN customers c ON c.id = doo.customer_id
                JOIN customer_addresses ca ON ca.id = mso.address_id
                JOIN delivery_receipts dr ON dr.meal_slot_order_id = mso.id
                LEFT JOIN customer_delivery_subscriptions cds ON cds.meal_slot_order_id = mso.id
                WHERE mso.status = 'DELIVERED'
                  AND dr.visible_to_customer = FALSE
                """);
        List<Object> params = new java.util.ArrayList<>();
        if (serveDate != null && !serveDate.isBlank()) {
            sql.append(" AND doo.serve_date = ?");
            params.add(java.sql.Date.valueOf(serveDate));
        }
        if (mealPeriod != null && !mealPeriod.isBlank()) {
            sql.append(" AND mso.meal_period = ?");
            params.add(mealPeriod);
        }
        sql.append(" ORDER BY doo.serve_date DESC, mso.id DESC");
        return jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> new DeliveryReleasePendingItem(
                rs.getLong("order_id"),
                rs.getDate("serve_date").toLocalDate().toString(),
                rs.getString("meal_period"),
                rs.getInt("quantity"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                rs.getString("delivery_address"),
                rs.getTimestamp("delivered_at") == null ? "" : rs.getTimestamp("delivered_at").toLocalDateTime().toString(),
                rs.getString("subscription_status")
            ),
            params.toArray()
        );
    }

    /** 立即释放指定订单：状态对用户可见 + 发送订阅消息 */
    @Transactional
    public DeliveryReleaseResult releaseOrder(long orderId) {
        String status = jdbcTemplate.query(
            "SELECT status FROM meal_slot_orders WHERE id = ?",
            ps -> ps.setLong(1, orderId),
            rs -> rs.next() ? rs.getString(1) : null
        );
        if (!"DELIVERED".equals(status)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "仅已送达的订单可立即释放");
        }
        com.jzqs.app.mobile.DeliverySubscriptionModule.DeliverySendResult sendResult =
            deliverySubscriptionModule.releaseAndSendWithReason(orderId);
        Long customerId = findCustomerIdByOrderId(orderId);
        if (customerId != null) {
            try {
                realtimeAudienceModule.publishCustomerEvent("customer.order.changed", customerId, orderId);
            } catch (RuntimeException ex) {
                // 实时推送失败不影响释放结果
            }
        }
        return new DeliveryReleaseResult(orderId, true, sendResult.sent(), sendResult.reason());
    }

    private Long findCustomerIdByOrderId(long orderId) {
        List<Long> customerIds = jdbcTemplate.query(
            """
                SELECT doo.customer_id
                FROM meal_slot_orders mso
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE mso.id = ?
                """,
            (rs, rowNum) -> rs.getLong("customer_id"),
            orderId
        );
        return customerIds.isEmpty() ? null : customerIds.get(0);
    }
}
