package com.jzqs.app.mobile.api;

import java.util.List;

public record MobileCurrentWeekResponse(
    String weekStartDate,
    String weekEndDate,
    boolean published,
    List<MobileCurrentWeekDayResponse> days
) {
}
