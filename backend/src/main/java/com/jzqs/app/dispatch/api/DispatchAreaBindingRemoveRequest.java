package com.jzqs.app.dispatch.api;

import com.jzqs.app.order.MealPeriod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DispatchAreaBindingRemoveRequest(
    MealPeriod mealPeriod,
    @NotBlank(message = "areaCode is required") String areaCode,
    @NotNull(message = "riderId is required") Long riderId
) {
}
