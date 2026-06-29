package com.jzqs.app.dispatch.api;

public record DispatchRiderAuthUnbindResponse(
    long riderId,
    String currentOpenid,
    String riderStatus
) {
}
