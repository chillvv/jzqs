package com.jzqs.app.customer.api;
public record CustomerAssetResponse(
    long id,
    String name,
    String phone,
    String customerStatus,
    int totalMeals,
    int remainingMeals,
    boolean hasOpenedCard,
    boolean fixedSubscriptionEnabled,
    boolean priorityCustomer,
    String priorityTag,
    String remark,
    String lastOrderAt,
    String registeredAt,
    String status
) {
}
