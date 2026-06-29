package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchAreaRiderAssignRequest(
    @NotBlank(message = "riderName is required") String riderName,
    String updatedBy
) {
}
