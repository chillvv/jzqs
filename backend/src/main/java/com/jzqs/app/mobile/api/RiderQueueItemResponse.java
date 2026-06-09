package com.jzqs.app.mobile.api;

public record RiderQueueItemResponse(
    long batchItemId,
    long batchId,
    long mealSlotOrderId,
    long addressId,
    int currentSequence,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    String mealPeriod,
    String mealName,
    int quantity,
    String note,
    String adminNote,
    String specialTag,
    boolean specialOrder,
    String specialSummary,
    String itemStatus,
    String receiptStatus,
    String receiptUrl,
    String receiptNote
) {
}
