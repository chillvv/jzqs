package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record MobileAddressUpsertRequest(
    @NotBlank String contactName,
    @NotBlank String contactPhone,
    @NotBlank String addressLine,
    @NotBlank String areaCode,
    boolean isDefault
) {
}
