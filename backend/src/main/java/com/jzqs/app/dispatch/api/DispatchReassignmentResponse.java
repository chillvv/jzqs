package com.jzqs.app.dispatch.api;

public record DispatchReassignmentResponse(
    long reassignmentId,
    String reassignLevel,
    long targetId,
    String fromRiderName,
    String toRiderName,
    String toAreaCode,
    String serveDate,
    String mealPeriod,
    boolean syncDefaultBinding,
    String reason,
    String createdBy,
    String createdAt
) {
}
