package com.jzqs.app.dispatch.api;

public record DispatchBatchResponse(
    long batchId,
    String serveDate,
    String mealPeriod,
    long riderProfileId,
    String riderName,
    String areaCode,
    String batchStatus,
    int totalCount,
    int deliveredCount,
    int currentSequence,
    String currentCustomerName,
    String nextCustomerName
) {
}
