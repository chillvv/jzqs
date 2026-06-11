package com.jzqs.app.dispatch.api;

public record DispatchAreaOrderItemResponse(
    long orderId,
    int sequenceNumber,
    String customerName,
    String deliveryAddress,
    String deliveryStatus,
    String riderName,
    String userNote,
    String adminNote,
    String referenceImageUrl,
    String receiptUrl,
    String receiptNote,
    String deliveredAt,
    int quantity
) {
}
