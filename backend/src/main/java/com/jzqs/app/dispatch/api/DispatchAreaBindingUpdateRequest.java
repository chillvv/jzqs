package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.Min;

public record DispatchAreaBindingUpdateRequest(
    String keywords,
    Long defaultRiderId,
    Long backupRiderId,
    String updatedBy
) {
}
