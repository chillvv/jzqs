package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;

public record ActivateRiderRequest(
    @NotBlank(message = "riderName is required") String riderName,
    String areaCode,
    String assignedBy
) {
}
