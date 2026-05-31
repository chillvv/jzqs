package com.jzqs.app.aftersale.api;

public record AdminAftersaleListItemResponse(
    long id,
    long orderId,
    long customerId,
    String customerName,
    String customerPhone,
    String serveDate,
    String mealPeriod,
    String orderStatus,
    String type,
    String status,
    String source,
    String reasonCode,
    String reasonText,
    boolean refundBlocking,
    String adminRemark,
    String requestedAt,
    String processedAt
) {
}
