package com.jzqs.app.delivery.api;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
public record DeliveryReceiptRequest(
    @Min(1) long mealSlotOrderId,
    @NotBlank String receiptUrl,
    String receiptNote,
    @NotBlank String deliveredAt
) {
}
