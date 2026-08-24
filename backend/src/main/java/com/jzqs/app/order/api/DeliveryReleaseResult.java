package com.jzqs.app.order.api;

/** 后台"送达状态立即释放"操作结果 */
public record DeliveryReleaseResult(
    long orderId,
    boolean released,
    boolean subscriptionSent,
    /** 取餐提醒发送结果原因：SENT=已发送、NO_CONSENT=客户未授权取餐模板、SEND_FAILED=微信发送失败、DISABLED=订阅通知开关关闭 */
    String subscriptionReason
) {
    public DeliveryReleaseResult(long orderId, boolean released, boolean subscriptionSent) {
        this(orderId, released, subscriptionSent, subscriptionSent ? "SENT" : "NO_CONSENT");
    }
}
