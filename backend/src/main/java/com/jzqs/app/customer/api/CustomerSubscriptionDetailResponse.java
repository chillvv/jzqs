package com.jzqs.app.customer.api;

public record CustomerSubscriptionDetailResponse(
    long id,
    boolean lunchEnabled,
    boolean dinnerEnabled,
    String startDate,
    String endDate,
    String merchantRemark,
    boolean priorityFollow,
    boolean paused
) {
}
