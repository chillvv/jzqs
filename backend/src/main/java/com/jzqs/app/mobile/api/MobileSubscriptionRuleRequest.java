package com.jzqs.app.mobile.api;
import jakarta.validation.constraints.NotNull;
import java.util.List;
public record MobileSubscriptionRuleRequest(
    @NotNull Boolean enabled,
    String weekDays,
    boolean lunchEnabled,
    boolean dinnerEnabled
) {
}