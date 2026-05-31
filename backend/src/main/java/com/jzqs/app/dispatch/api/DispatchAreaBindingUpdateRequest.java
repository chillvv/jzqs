package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DispatchAreaBindingUpdateRequest(
    String keywords,
    Long defaultRiderId,
    Long backupRiderId,
    @NotBlank(message = "updatedBy is required") String updatedBy
) {
}
