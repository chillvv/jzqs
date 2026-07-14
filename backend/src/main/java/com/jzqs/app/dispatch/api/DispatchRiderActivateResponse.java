package com.jzqs.app.dispatch.api;

public record DispatchRiderActivateResponse(
    long riderId,
    String riderStatus,
    String areaCode
) {
}
