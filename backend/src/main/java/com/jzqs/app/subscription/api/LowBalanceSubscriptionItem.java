package com.jzqs.app.subscription.api;

import java.time.LocalDate;

public record LowBalanceSubscriptionItem(
    long customerId,
    String customerName,
    String customerPhone,
    int remainingMeals,
    String packageExpiredAt,
    int remainingValidityDays,
    String packageAlertCode,
    String packageAlertLabel,
    boolean lunchEnabled,
    boolean dinnerEnabled,
    LocalDate nextServeDate,
    long subscriptionRuleId
) {
}
