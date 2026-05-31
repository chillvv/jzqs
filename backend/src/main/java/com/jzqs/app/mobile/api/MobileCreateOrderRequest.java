package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record MobileCreateOrderRequest(
    @NotBlank(message = "serveDate is required")
    String serveDate,
    @NotBlank(message = "mealPeriod is required")
    String mealPeriod,
    String deliveryAddress,
    String note
) {
}
