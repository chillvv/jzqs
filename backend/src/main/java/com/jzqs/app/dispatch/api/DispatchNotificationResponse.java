package com.jzqs.app.dispatch.api;

public record DispatchNotificationResponse(
    long dispatchId,
    String notificationStatus
) {}
