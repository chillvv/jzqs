package com.jzqs.app.dispatch.api;

public record PendingRiderResponse(
    long riderId,
    String displayName,
    String phone,
    String currentOpenid,
    String authStatus,
    String firstLoginAt,
    String lastLoginAt
) {
}
