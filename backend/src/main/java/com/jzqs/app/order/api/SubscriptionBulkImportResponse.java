package com.jzqs.app.order.api;

import java.util.List;

public record SubscriptionBulkImportResponse(
    int successCount,
    int failureCount,
    List<FailureItem> failures
) {
    public record FailureItem(
        long customerId,
        String customerName,
        String reason,
        int remainingMeals,
        int requiredMeals
    ) {
    }
}
