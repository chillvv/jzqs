package com.jzqs.app.mobile.api;

public record RiderQueueItemActionResponse(
    long batchItemId,
    String itemStatus,
    String status
) {
}
