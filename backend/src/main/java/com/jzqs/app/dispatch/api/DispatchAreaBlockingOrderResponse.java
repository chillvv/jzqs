package com.jzqs.app.dispatch.api;

public record DispatchAreaBlockingOrderResponse(
    long orderId,
    String customerName,
    String deliveryAddress,
    String deliveryStatus,
    String serveDate
) {
}
