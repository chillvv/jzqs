package com.jzqs.app.subscription.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SubscriptionRuleRequest(
    @NotNull Long customerId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    boolean lunchEnabled,
    int lunchQuantity,
    String lunchDeliveryMealPeriod,
    boolean dinnerEnabled,
    int dinnerQuantity,
    String dinnerDeliveryMealPeriod,
    Long defaultAddressId,
    String merchantRemark,
    boolean isPriorityFollow
) {
}
