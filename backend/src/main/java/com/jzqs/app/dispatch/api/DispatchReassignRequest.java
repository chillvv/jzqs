package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DispatchReassignRequest(
    @NotBlank(message = "reassignLevel is required") String reassignLevel,
    @Min(value = 1, message = "targetId is required") long targetId,
    String fromRiderName,
    @NotBlank(message = "toRiderName is required") String toRiderName,
    String toAreaCode,
    @NotBlank(message = "serveDate is required") String serveDate,
    String mealPeriod,
    @NotNull(message = "syncDefaultBinding is required") Boolean syncDefaultBinding,
    String reason,
    String createdBy
) {
}
