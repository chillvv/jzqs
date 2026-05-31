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
    boolean lunchEnabled,
    int lunchQuantity,
    boolean dinnerEnabled,
    int dinnerQuantity,
    Long defaultAddressId,
    String defaultAddress,
    String defaultNote,
    boolean isPriorityFollow,
    boolean paused,
    boolean active,
    String status,
    int remainingMeals,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
