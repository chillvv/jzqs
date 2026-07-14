package com.jzqs.app.dispatch.api;

public record DispatchRouteLabStartResponse(
    long logId,
    String status,
    String message
) {
}
