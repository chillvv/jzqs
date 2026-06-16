package com.jzqs.app.order.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubscriptionImportItem(
    long customerId,
    @NotBlank String mealPeriod,
    String deliveryMealPeriod,
    long addressId,
    String note
) {
}
