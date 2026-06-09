package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchRiderUpdateRequest(
    @NotBlank(message = "riderName is required") String riderName,
    @NotBlank(message = "displayName is required") String displayName,
    @NotBlank(message = "phone is required") String phone,
    String areaCode,
    @NotBlank(message = "updatedBy is required") String updatedBy
) {
}
