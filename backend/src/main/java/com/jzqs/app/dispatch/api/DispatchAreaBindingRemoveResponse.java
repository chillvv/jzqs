package com.jzqs.app.dispatch.api;

public record DispatchAreaBindingRemoveResponse(
    String areaCode,
    long riderId,
    String status
) {
}
