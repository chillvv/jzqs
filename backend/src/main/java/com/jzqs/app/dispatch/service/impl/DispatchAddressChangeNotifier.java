package com.jzqs.app.dispatch.service.impl;

import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 「配送地址变更」通知：后台改地址后，把变更明确推给当事骑手。
 *
 * 背景（2026-09 商家反馈）：商家后台改址后，订单的派单会被静默撤销/换址
 * （见 MobileAddressModule.reconcileDispatchArea），或者地址簿被直接改写导致
 * 订单配送目标变化；骑手端只在收到 dispatch.* 事件时「静默刷新列表」，
 * 既不会弹窗也不会提示，结果骑手按旧地址继续送，最后送错。
 *
 * 本组件负责把「哪一单的地址变成了什么」定点推给当事骑手（+ admin），
 * 骑手端收到后显式弹窗提醒。覆盖两条后台改址路径：
 *   1) 订单中心「更换地址」→ notifyOrderAddressChanged
 *   2) 客户管理「编辑地址」→ notifyCustomerAddressChanged
 *
 * 注意：只推给当事骑手，不广播 rider:all，避免无关骑手收到别人的改址提醒。
 */
@Component
public class DispatchAddressChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(DispatchAddressChangeNotifier.class);

    /** 骑手端据此事件弹窗提醒（勿随意改名，需与 miniapp-rider 的 address-change-notice 同步）。 */
    public static final String EVENT_ADDRESS_CHANGED = "dispatch.order.address.changed";

    /** 商家后台代客改址（订单中心「更换地址」）。 */
    public static final String SOURCE_ADMIN_CHANGED_ADDRESS = "ADMIN_CHANGED_ADDRESS";

    /** 顾客自己在小程序改址。 */
    public static final String SOURCE_CUSTOMER_CHANGED_ADDRESS = "CUSTOMER_CHANGED_ADDRESS";

    /** 商家在客户管理里编辑了地址簿（会连带影响所有引用该地址的订单）。 */
    public static final String SOURCE_ADDRESS_BOOK_UPDATED = "ADDRESS_BOOK_UPDATED";

    /**
     * 对外展示的配送地址文本：定位地址 + 门牌号。
     * 与骑手端 RiderQueueSupport、分单端 DispatchQueryModule 拼接口径保持一致。
     */
    private static final String ADDRESS_TEXT_EXPR =
        "CASE WHEN ca.door_number IS NOT NULL AND ca.door_number <> '' "
            + "THEN CONCAT(ca.address_line, ' ', ca.door_number) "
            + "ELSE ca.address_line END";

    /** 「还没送完且已派给骑手」的订单才算需要通知，已完结订单通知骑手只会变成噪音。 */
    private static final String ACTIVE_ORDER_CONDITION =
        "mso.status NOT IN ('DELIVERED', 'CANCELLED', 'REFUNDED') "
            + "AND da.rider_name IS NOT NULL AND da.rider_name <> ''";

    private final JdbcTemplate jdbcTemplate;
    private final RealtimeAudienceModule realtimeAudienceModule;

    public DispatchAddressChangeNotifier(JdbcTemplate jdbcTemplate, RealtimeAudienceModule realtimeAudienceModule) {
        this.jdbcTemplate = jdbcTemplate;
        this.realtimeAudienceModule = realtimeAudienceModule;
    }

    /**
     * 订单改址通知：推给「改址前承接该单」的骑手。
     *
     * <b>必须在撤销原派单（OrderDispatchRepository.resetDispatchFlow）之前调用</b>：
     * 派单行一旦删除就再也查不到原骑手，骑手也就永远不知道自己这单变址了。
     *
     * @return 被通知的骑手名；该单未派单（或已完结）时返回 null
     */
    public String notifyOrderAddressChanged(long orderId, String source) {
        String sql = """
            SELECT
                mso.id AS order_id,
                c.name AS customer_name,
                da.rider_name AS rider_name,
                %s AS address_text
            FROM meal_slot_orders mso
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            JOIN customers c ON c.id = doo.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            JOIN dispatch_assignments da ON da.meal_slot_order_id = mso.id
            WHERE mso.id = ?
              AND %s
            """.formatted(ADDRESS_TEXT_EXPR, ACTIVE_ORDER_CONDITION);
        List<AddressChangeNoticeRow> rows = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new AddressChangeNoticeRow(
                rs.getLong("order_id"),
                rs.getString("customer_name"),
                rs.getString("rider_name"),
                rs.getString("address_text")
            ),
            orderId
        );
        if (rows.isEmpty()) {
            return null;
        }
        AddressChangeNoticeRow row = rows.get(0);
        publish(row, source);
        log.info("改址通知已推送: orderId={} rider={} source={} reason=订单换地址",
            row.orderId(), row.riderName(), source);
        return row.riderName();
    }

    /**
     * 地址簿改址通知：客户管理里编辑地址会静默改变所有引用该地址订单的配送目标，
     * 因此凡「还没送完、且已派给骑手」的订单，都要通知对应骑手。
     *
     * @param previousAddressText 改址前的地址文本（用 {@link #readAddressText} 读取）；
     *                            与改后文本相同（例如只改了联系人电话）时不打扰骑手
     * @return 被通知的订单数
     */
    public int notifyCustomerAddressChanged(long addressId, String previousAddressText, String source) {
        String sql = """
            SELECT
                mso.id AS order_id,
                c.name AS customer_name,
                da.rider_name AS rider_name,
                %s AS address_text
            FROM meal_slot_orders mso
            JOIN daily_orders doo ON doo.id = mso.daily_order_id
            JOIN customers c ON c.id = doo.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            JOIN dispatch_assignments da ON da.meal_slot_order_id = mso.id
            WHERE mso.address_id = ?
              AND %s
            """.formatted(ADDRESS_TEXT_EXPR, ACTIVE_ORDER_CONDITION);
        List<AddressChangeNoticeRow> rows = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new AddressChangeNoticeRow(
                rs.getLong("order_id"),
                rs.getString("customer_name"),
                rs.getString("rider_name"),
                rs.getString("address_text")
            ),
            addressId
        );
        if (rows.isEmpty()) {
            return 0;
        }
        if (isSameText(previousAddressText, rows.get(0).addressText())) {
            // 只改了联系人/电话等不影响配送位置的信息，不制造假提醒。
            return 0;
        }
        rows.forEach(row -> publish(row, source));
        log.info("改址通知已推送: addressId={} 订单数={} source={} reason=地址簿编辑",
            addressId, rows.size(), source);
        return rows.size();
    }

    /** 读取地址当前的对外文本（定位地址 + 门牌号），供改址前后比对。 */
    public String readAddressText(long addressId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT " + ADDRESS_TEXT_EXPR + " AS address_text FROM customer_addresses ca WHERE ca.id = ?",
            (rs, rowNum) -> rs.getString("address_text"),
            addressId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void publish(AddressChangeNoticeRow row, String source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", row.orderId());
        payload.put("customerName", text(row.customerName()));
        payload.put("addressText", text(row.addressText()));
        payload.put("source", text(source));
        payload.put("noticeText", buildNoticeText(row, source));
        realtimeAudienceModule.publishRiderDirectEvent(EVENT_ADDRESS_CHANGED, row.riderName(), payload);
    }

    /** 服务端生成提醒文案，保证各端口径一致（骑手端弹窗直接展示 noticeText）。 */
    private String buildNoticeText(AddressChangeNoticeRow row, String source) {
        String customer = text(row.customerName());
        String address = text(row.addressText());
        String who = SOURCE_CUSTOMER_CHANGED_ADDRESS.equals(source) ? "客户" : "商家";
        StringBuilder text = new StringBuilder("订单客户「").append(customer).append('」');
        if (address.isEmpty()) {
            text.append("的配送地址已变更");
        } else {
            text.append("的配送地址已变更为「").append(address).append('」');
        }
        return text.append("（").append(who).append("修改），请按最新地址配送，并核对订单顺序。").toString();
    }

    private boolean isSameText(String left, String right) {
        return Objects.equals(text(left), text(right));
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    record AddressChangeNoticeRow(long orderId, String customerName, String riderName, String addressText) {
    }
}
