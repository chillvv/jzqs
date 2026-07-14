package com.jzqs.app.dispatch.api;

public record DispatchAreaRiderAssignResponse(
    String areaCode,
    int assignedCount,
    String mealPeriod
) {
}
