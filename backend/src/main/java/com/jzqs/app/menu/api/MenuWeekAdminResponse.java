package com.jzqs.app.menu.api;

import java.util.List;

public record MenuWeekAdminResponse(
    long weekId,
    String weekStartDate,
    String weekEndDate,
    String status,
    List<MenuWeekDayResponse> days
) {
}
