package com.jzqs.app.mobile.api;

import java.util.List;

public record MobileCurrentWeekResponse(
    String weekStartDate,
    String weekEndDate,
    List<MobileCurrentWeekDayResponse> days
) {
}
