package com.jzqs.app.dispatch.api;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
public record DispatchAssignRequest(
    @Min(1) long mealSlotOrderId,
    @NotBlank String riderName,
    @NotBlank String areaCode
) {
}
