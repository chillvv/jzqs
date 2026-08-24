package com.jzqs.app.dispatch.api;

import com.jzqs.app.order.MealPeriod;

public record DispatchManagedRiderResponse(
    long riderId,
    MealPeriod mealPeriod,
    String riderName,
    String displayName,
    String phone,
    String authStatus,
    String employmentStatus,
    String areaCode,
    String assignedBy,
    String firstLoginAt,
    String lastLoginAt,
    int todayTaskCount,
    int todayDeliveredCount,
    String currentOpenid
) {
}
