package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record RiderAddressReferenceUpdateRequest(
    @NotBlank(message = "referenceImageUrl is required")
    String referenceImageUrl
) {
}
