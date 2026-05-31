package com.jzqs.app.mobile.api;

public record RiderBatchSummaryResponse(
    String riderName,
    int totalCount,
    int deliveredCount,
    int remainingCount,
    BatchCardResponse lunchBatch,
    BatchCardResponse dinnerBatch
) {
    public record BatchCardResponse(
        long batchId,
        String mealPeriod,
        String batchStatus,
        int totalCount,
        int deliveredCount,
        int remainingCount,
        int currentSequence,
        String currentCustomerName,
        String nextCustomerName
    ) {
    }
}
