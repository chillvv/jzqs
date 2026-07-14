package com.jzqs.app.aftersale.api;

public record AdminAftersaleOrderOptionResponse(
    long orderId,
    String customerName,
    String customerPhone,
    String serveDate,
    String mealPeriod,
    String orderStatus,
    String addressSummary
) {
}
