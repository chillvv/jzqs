package com.jzqs.app.dispatch.api;

public record DispatchExceptionItemResponse(
    long mealSlotOrderId,
    String exceptionType,
    String reason,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    String suggestedAreaCode,
    String suggestedRiderName,
    boolean rememberedAddress
) {
}
