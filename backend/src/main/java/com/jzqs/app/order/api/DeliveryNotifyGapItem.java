package com.jzqs.app.order.api;

/**
 * 后台「取餐提醒未送达名单」条目：骑手已送达，但取餐提醒订阅消息确定发不出去、需要人工补漏的订单。
 *
 * <p>只收录三类终态，避免把「还在重试」「尚未到释放时间」的正常订单误报成漏发：
 * <ul>
 *   <li>{@code NO_SUBSCRIPTION}：该订单没有任何订阅授权记录（如后台代客下单、前端授权落库丢失）；</li>
 *   <li>{@code REVOKED}：用户已在微信端关闭该模板的订阅授权，属终态，系统不会再发；</li>
 *   <li>{@code RETRY_EXHAUSTED}：发送失败且重试次数已耗尽。</li>
 * </ul>
 *
 * @param reason 见上；前端按此映射给运营看的文案与颜色
 */
public record DeliveryNotifyGapItem(
    long orderId,
    String serveDate,
    String mealPeriod,
    int quantity,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    String deliveredAt,
    /** daily_orders.source：MINIAPP / BACKEND / SUBSCRIPTION */
    String orderSource,
    /** 该订单是否由固定订餐（订阅确认）生成，口径与订单中心 mso.confirmed_from_subscription 一致 */
    boolean fixedSubscription,
    String reason
) {
}
