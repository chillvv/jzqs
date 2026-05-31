package com.jzqs.app.menu.api;

import java.util.List;

public record MenuWeekDaySlotRequest(
    String slotStatus,
    List<String> dishItems,
    Integer totalCalories,
    String merchantNote,
    String imageUrl
) {
}
