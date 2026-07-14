package com.jzqs.app.mobile.api;

public record RiderOrderSequenceSaveResponse(
    boolean success,
    String message,
    long batchId,
    int updatedCount
) {
}
