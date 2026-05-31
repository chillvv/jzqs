package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record RiderReceiptRequest(
    @NotBlank(message = "riderName is required")
    String riderName,
    String receiptFileKey,
    String receiptNote,
    String deliveredAt
) {
}
