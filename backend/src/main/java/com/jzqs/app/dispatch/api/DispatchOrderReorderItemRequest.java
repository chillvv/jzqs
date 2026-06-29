package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DispatchOrderReorderItemRequest(
    @NotNull(message = "orderId is required") Long orderId,
    @NotNull(message = "sequenceNumber is required")
    @Min(value = 1, message = "sequenceNumber must be greater than 0")
    Integer sequenceNumber
) {
}
