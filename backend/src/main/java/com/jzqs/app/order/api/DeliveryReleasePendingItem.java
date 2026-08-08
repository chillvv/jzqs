package com.jzqs.app.order.api;

/** 后台"送达状态立即释放"待释放订单项 */
public record DeliveryReleasePendingItem(
    long orderId,
    String serveDate,
    String mealPeriod,
    int quantity,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    String deliveredAt,
    String subscriptionStatus
) {
}
