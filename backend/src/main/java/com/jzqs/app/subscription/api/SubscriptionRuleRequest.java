package com.jzqs.app.subscription.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SubscriptionRuleRequest(
    @NotNull Long customerId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    boolean lunchEnabled,
    int lunchQuantity,
    boolean dinnerEnabled,
    int dinnerQuantity,
    Long defaultAddressId,
    String defaultNote,
    boolean isPriorityFollow
) {
}
