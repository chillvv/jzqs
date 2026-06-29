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
    String merchantRemark,
    String openedAt,
    String packageExpiredAt,
    int remainingValidityDays,
    String packageAlertCode,
    String packageAlertLabel,
    String lastOrderAt,
    String registeredAt,
    String status
) {
}
