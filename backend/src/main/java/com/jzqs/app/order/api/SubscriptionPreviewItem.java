package com.jzqs.app.order.api;

public record SubscriptionPreviewItem(
    long customerId,
    String customerName,
    String customerPhone,
    String mealPeriod,
    String deliveryMealPeriod,
    long addressId,
    String deliveryAddress,
    String merchantRemark,
    int remainingMeals,
    boolean hasBalance
) {
}
