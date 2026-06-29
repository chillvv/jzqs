package com.jzqs.app.delivery.api;

public record DeliveryReceiptDeleteResponse(
    long mealSlotOrderId,
    String orderStatus,
    String receiptUrl,
    boolean deleted
) {
}
