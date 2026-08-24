package com.jzqs.app.dispatch.api;

import com.jzqs.app.order.MealPeriod;
import jakarta.validation.constraints.Min;

public record DispatchAreaBindingUpdateRequest(
    MealPeriod mealPeriod,
    String keywords,
    Long defaultRiderId,
    Long backupRiderId,
    String updatedBy
) {
}
