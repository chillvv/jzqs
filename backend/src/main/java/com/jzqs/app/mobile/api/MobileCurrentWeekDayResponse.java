package com.jzqs.app.mobile.api;

import java.util.List;

public record MobileCurrentWeekDayResponse(
    String serveDate,
    String weekdayLabel,
    String slotStatus,
    List<MobileMenuItemResponse> items
) {
}
