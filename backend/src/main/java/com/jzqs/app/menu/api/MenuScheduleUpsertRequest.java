package com.jzqs.app.menu.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record MenuScheduleUpsertRequest(
    @NotBlank String serveDate,
    @NotBlank String mealPeriod,
    @NotBlank String mealName,
    @NotBlank String mealDetail,
    @Min(1) int calories,
    @NotBlank String merchantNote
) {
}
