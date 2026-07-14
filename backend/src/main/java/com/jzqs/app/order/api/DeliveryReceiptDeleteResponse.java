package com.jzqs.app.order.api;

public record DeliveryReceiptDeleteResponse(
    long mealSlotOrderId,
    String orderStatus,
    String receiptUrl,
    boolean deleted
) {
}
