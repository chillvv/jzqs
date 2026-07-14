package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DispatchAreaBindingRemoveRequest(
    @NotBlank(message = "areaCode is required") String areaCode,
    @NotNull(message = "riderId is required") Long riderId
) {
}
