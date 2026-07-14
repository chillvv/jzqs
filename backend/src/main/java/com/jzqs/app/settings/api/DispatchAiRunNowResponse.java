package com.jzqs.app.settings.api;

public record DispatchAiRunNowResponse(
    String serveDate,
    String mealPeriod,
    int scannedAreaCount,
    int successCount,
    int failedCount,
    String message
) {
}
