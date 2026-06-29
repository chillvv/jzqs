package com.jzqs.app.mobile.api;
public record MobileSubscriptionRuleResponse(
    boolean enabled,
    String weekDays,
    boolean lunchEnabled,
    boolean dinnerEnabled
) {
}