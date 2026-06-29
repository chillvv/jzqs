package com.jzqs.app.dispatch.api;

public record DispatchRiderStatusResponse(
    long riderId,
    String riderStatus
) {}
