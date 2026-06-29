package com.jzqs.app.mobile.api;

public record MobileAfterSaleItemResponse(
    long id,
    long orderId,
    String type,
    String status,
    String reasonCode,
    String reasonText,
    String adminRemark,
    String requestedAt,
    String processedAt
) {
}
