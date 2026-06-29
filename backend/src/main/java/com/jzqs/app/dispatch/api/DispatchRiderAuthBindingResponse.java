package com.jzqs.app.dispatch.api;

public record DispatchRiderAuthBindingResponse(
    long riderId,
    String riderName,
    String displayName,
    String phone,
    String currentOpenid,
    String authStatus,
    String lastLoginAt
) {
}
