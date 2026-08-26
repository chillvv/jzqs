package com.jzqs.app.dispatch.api;

public record DispatchRiderProfileUpsertResponse(
    long riderId,
    String riderName,
    String displayName,
    String phone,
    String areaCode,
    String riderStatus
) {
}
