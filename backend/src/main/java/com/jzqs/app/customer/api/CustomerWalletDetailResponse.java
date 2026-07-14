package com.jzqs.app.customer.api;

public record CustomerWalletDetailResponse(
    int totalMeals,
    int reservedMeals,
    int consumedMeals,
    int remainingMeals,
    String openedAt,
    String expiredAt,
    Integer remainingValidityDays,
    String packageAlertCode,
    String packageAlertLabel
) {
}
