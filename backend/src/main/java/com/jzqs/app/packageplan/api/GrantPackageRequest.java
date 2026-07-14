package com.jzqs.app.packageplan.api;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
public record GrantPackageRequest(
    @Min(1) long customerId,
    @NotBlank String packageCode,
    @Min(1) int totalMeals
) {
}
