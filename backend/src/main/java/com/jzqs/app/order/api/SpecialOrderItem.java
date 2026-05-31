package com.jzqs.app.order.api;

public record SpecialOrderItem(
    long id,
    String customerName,
    String customerPhone,
    String addressLine,
    String mealPeriod,
    int quantity,
    String userNote,
    String adminNote,
    String specialTag,
    boolean priorityCustomer
) {
}
