package com.jzqs.app.dispatch.api;

public record DispatchOrderAssignResponse(
    long mealSlotOrderId,
    String riderName,
    String status
) {
}
