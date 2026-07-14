package com.jzqs.app.mobile.api;

import java.util.List;

public record MobileMenuItemResponse(
    long id,
    String serveDate,
    String mealPeriod,
    List<String> dishItems,
    Integer totalCalories,
    String merchantNote,
    String status
) {
}
