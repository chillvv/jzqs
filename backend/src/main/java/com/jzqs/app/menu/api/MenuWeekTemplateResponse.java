package com.jzqs.app.menu.api;

public record MenuWeekTemplateResponse(
    long weekId,
    String weekStartDate,
    String weekEndDate,
    String status
) {}
