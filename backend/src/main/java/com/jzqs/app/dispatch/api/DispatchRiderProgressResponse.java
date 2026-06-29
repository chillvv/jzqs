package com.jzqs.app.dispatch.api;

public record DispatchRiderProgressResponse(
    String riderName,
    String areaCode,
    int completedCount,
    int totalCount,
    long currentOrderId,
    int currentSequenceNumber,
    long nextOrderId,
    int pendingCount,
    int exceptionCount
) {
}
