package com.jzqs.app.dispatch.api;

public record DispatchExceptionAreaConfirmResponse(
    long mealSlotOrderId,
    String areaCode,
    String riderName,
    boolean rememberAddress,
    String updatedBy,
    String status
) {}
