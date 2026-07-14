package com.jzqs.app.aftersale.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AdminAftersaleCreateRequest(
    @Min(1) long orderId,
    @NotBlank String type,
    @NotBlank String reasonCode,
    @NotBlank String reasonText,
    String issueParamSummary,
    @Min(0) int estimatedLossMeals,
    String sourceCategory,
    String remark,
    String operatorName
) {
}
