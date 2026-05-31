package com.jzqs.app.order.api;
public record OrderPrepStatsResponse(
    int totalMeals,
    int lunchCount,
    int dinnerCount,
    int selfOrderCount,
    int staffOrderCount,
    int subscriptionCount,
    int specialOrderCount,
    int adminRemarkCount,
    int labelRequiredCount
) {
}
