package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DispatchTakeoverAuthRequest(
    @Min(value = 1, message = "sourceRiderId is required") long sourceRiderId,
    @NotBlank(message = "assignedBy is required") String assignedBy
) {
}
