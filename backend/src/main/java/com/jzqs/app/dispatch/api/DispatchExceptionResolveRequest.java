package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchExceptionResolveRequest(
    @NotBlank String riderName,
    @NotBlank String areaCode
) {
}
