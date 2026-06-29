package com.jzqs.app.mobile.api;

public record RiderOrderStatusRevertResponse(
    long orderId,
    String newStatus
) {
}
