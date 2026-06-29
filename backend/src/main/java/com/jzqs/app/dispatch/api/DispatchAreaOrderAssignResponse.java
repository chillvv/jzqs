package com.jzqs.app.dispatch.api;

public record DispatchAreaOrderAssignResponse(
    String areaCode,
    long orderId,
    String status
) {
}
