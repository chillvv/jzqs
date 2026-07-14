package com.jzqs.app.menu.api;

public record MenuWeekCopyResponse(
    long weekId,
    String weekStartDate,
    String weekEndDate,
    String status,
    String copiedFromWeekStart
) {}
