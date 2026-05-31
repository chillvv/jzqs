package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchExceptionConfirmRequest(
    @NotBlank String areaCode,
    @NotBlank String riderName,
    Boolean rememberAddress,
    @NotBlank String updatedBy
) {
}
