package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record MobileCreateAfterSaleRequest(
    @NotBlank String type,
    @NotBlank String reasonCode,
    @NotBlank String reasonText,
    String remark
) {
}
