package com.jzqs.app.dispatch.api;

import com.jzqs.app.order.MealPeriod;

public record DispatchRiderProfileUpsertResponse(
    long riderId,
    MealPeriod mealPeriod,
    String riderName,
    String displayName,
    String phone,
    String areaCode,
    String riderStatus
) {
}
