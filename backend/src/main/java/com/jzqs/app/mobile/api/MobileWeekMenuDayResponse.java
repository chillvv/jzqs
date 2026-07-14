package com.jzqs.app.mobile.api;

import java.util.List;

public record MobileWeekMenuDayResponse(
    String serveDate,
    String weekdayLabel,
    List<MobileMenuItemResponse> items
) {
}
