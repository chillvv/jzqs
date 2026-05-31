package com.jzqs.app.mobile.api;

public record RiderQueueItemResponse(
    long batchItemId,
    long batchId,
    long mealSlotOrderId,
    int currentSequence,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    String mealPeriod,
    String mealName,
    int quantity,
    String note,
    String itemStatus,
    String receiptStatus,
    String receiptUrl,
    String receiptNote
) {
}
