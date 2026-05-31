package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchAreaDeleteRequest(
    @NotBlank(message = "areaCode is required") String areaCode
) {
}
