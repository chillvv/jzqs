package com.jzqs.app.order.api;
public record OrderPrepItemResponse(
    long id,
    String customerName,
    String customerPhone,
    String mealPeriod,
    String deliveryMealPeriod,
    String mealSummary,
    int quantity,
    String userNote,
    /** 展示用的商家备注：快照条目 + 订单列值，去重后逗号拼接。 */
    String merchantRemark,
    /** 只属于这一单的商家备注（订单列原始值），供后台编辑用，不含客户长期备注。 */
    String orderMerchantRemark,
    String deliveryAddress,
    String source,
    String areaCode,
    String riderName,
    boolean priorityCustomer,
    boolean fixedSubscription,
    String status,
    String displayStatus,
    String displayStatusLabel,
    String createdAt,
    boolean canAssign,
    boolean canCancel,
    boolean canReceipt,
    String referenceImageUrl,
    String receiptUrl,
    String receiptNote,
    String deliveredAt,
    long customerId,
    String serveDate
) {
}
