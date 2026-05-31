package com.jzqs.app.subscription.api;

import java.util.List;

public record SubscriptionPreviewCheckResponse(
    int totalCount,
    int sufficientCount,
    int insufficientCount,
    List<InsufficientCustomer> insufficientCustomers
) {
    public record InsufficientCustomer(
        long customerId,
        String customerName,
        String customerPhone,
        int remainingMeals,
        int requiredMeals,
        String mealPeriod
    ) {
    }
}
