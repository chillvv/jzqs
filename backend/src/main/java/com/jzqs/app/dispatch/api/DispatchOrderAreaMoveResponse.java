package com.jzqs.app.dispatch.api;

public record DispatchOrderAreaMoveResponse(
    String areaCode,
    long orderId,
    String targetAreaCode
) {
}
