package com.jzqs.app.dispatch.api;

public record DispatchPendingItemResponse(
    long orderId,
    String customerName,
    String deliveryAddress
) {
}
