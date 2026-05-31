package com.jzqs.app.order.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ManualCreateOrderRequest(
    @Min(1) long customerId,
    @Min(1) Long addressId,
    @NotBlank String mealPeriod,
    @NotBlank String note,
    String deliveryAddress,
    @NotBlank String source,
    @Min(1) Integer quantity,
    String serveDate
) {
    public int quantityOrDefault() {
        return quantity != null ? quantity : 1;
    }
}
