package com.jzqs.app.order.api;

public record SubscriptionPreviewItem(
    long customerId,
    String customerName,
    String customerPhone,
    String mealPeriod,
    long addressId,
    String deliveryAddress,
    String merchantRemark,
    int remainingMeals,
    boolean hasBalance
) {
}
