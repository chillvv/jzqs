package com.jzqs.app.menu.api;

public record MenuWeekDayResponse(
    String serveDate,
    String weekdayLabel,
    MenuWeekDaySlotResponse lunch,
    MenuWeekDaySlotResponse dinner
) {
}
