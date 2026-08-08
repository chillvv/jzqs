package com.jzqs.app.order.api;

/** 后台"送达状态立即释放"操作结果 */
public record DeliveryReleaseResult(
    long orderId,
    boolean released,
    boolean subscriptionSent
) {
}
