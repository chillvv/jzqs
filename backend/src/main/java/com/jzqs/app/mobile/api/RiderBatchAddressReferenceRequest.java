package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RiderBatchAddressReferenceRequest(
    @NotEmpty(message = "addressIds is required")
    List<Long> addressIds,
    @NotBlank(message = "referenceImageUrl is required")
    String referenceImageUrl
) {
}
