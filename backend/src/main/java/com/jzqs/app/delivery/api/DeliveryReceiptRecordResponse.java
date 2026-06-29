package com.jzqs.app.delivery.api;

public record DeliveryReceiptRecordResponse(
    long mealSlotOrderId,
    String orderStatus,
    String walletAction,
    String notificationStatus,
    String receiptUrl,
    String visibleAt,
    String expiresAt
) {
}
