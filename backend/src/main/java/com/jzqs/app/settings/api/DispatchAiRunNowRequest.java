package com.jzqs.app.settings.api;

public record DispatchAiRunNowRequest(
    String serveDate,
    String mealPeriod,
    String areaCode
) {
}
