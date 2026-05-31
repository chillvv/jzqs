package com.jzqs.app.customer.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record WalletAdjustRequest(
    @Min(1) int mealDelta,
    @NotBlank String operatorName,
    @NotBlank String remark
) {
}
