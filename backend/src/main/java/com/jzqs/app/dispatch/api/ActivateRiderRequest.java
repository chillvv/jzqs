package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;

public record ActivateRiderRequest(
    @NotBlank(message = "riderName is required") String riderName,
    String areaCode,
    @NotBlank(message = "assignedBy is required") String assignedBy
) {
}
