package com.jzqs.app.subscription.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SubscriptionRuleResponse(
    long id,
    long customerId,
    String customerName,
    String customerPhone,
    LocalDate startDate,
    LocalDate endDate,
    String weekDays,
    boolean lunchEnabled,
    int lunchQuantity,
    String lunchDeliveryMealPeriod,
    boolean dinnerEnabled,
    int dinnerQuantity,
    String dinnerDeliveryMealPeriod,
    Long defaultAddressId,
    String defaultAddress,
    String merchantRemark,
    boolean isPriorityFollow,
    boolean paused,
    boolean active,
    String status,
    int remainingMeals,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
