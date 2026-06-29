package com.jzqs.app.dispatch.api;

public record DispatchRiderAuthTakeoverResponse(
    long riderId,
    long sourceRiderId,
    String currentOpenid,
    String riderStatus
) {
}
