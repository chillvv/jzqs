package com.jzqs.app.order.api;

public record SubscriptionConfirmationItem(
    long id,
    String customerName,
    String customerPhone,
    String mealPeriod,
    int quantity,
    String addressLine,
    String userNote,
    String merchantRemark,
    boolean priority,
    String status
) {
}
