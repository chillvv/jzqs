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
    String merchantRemark,
    String deliveryAddress,
    String source,
    boolean priorityCustomer,
    boolean fixedSubscription,
    String status,
    String displayStatus,
    String displayStatusLabel,
    boolean canAssign,
    boolean canCancel,
    boolean canReceipt,
    String walletStatusLabel,
    String referenceImageUrl,
    String receiptUrl,
    String receiptNote,
    String deliveredAt
) {
}
