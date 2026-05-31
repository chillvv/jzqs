package com.jzqs.app.mobile.api;

public record RiderTaskItemResponse(
    long dispatchId,
    long mealSlotOrderId,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    String mealPeriod,
    String mealName,
    String note,
    String deliveryStatus,
    String receiptStatus,
    String receiptUrl
) {
}
