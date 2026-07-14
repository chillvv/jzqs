package com.jzqs.app.menu.api;

public record MenuWeekDaySaveResponse(
    long weekId,
    String serveDate,
    String status
) {}
