package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchCreateRiderRequest(
    @NotBlank String riderName,
    @NotBlank String displayName,
    @NotBlank String phone,
    String areaCode,
    String employmentStatus,
    String updatedBy
) {
}
