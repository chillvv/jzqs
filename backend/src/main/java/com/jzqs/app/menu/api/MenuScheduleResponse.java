package com.jzqs.app.menu.api;
public record MenuScheduleResponse(
    long id,
    String serveDate,
    String mealPeriod,
    String mealName,
    String mealDetail,
    int calories,
    String merchantNote,
    String status
) {
}
