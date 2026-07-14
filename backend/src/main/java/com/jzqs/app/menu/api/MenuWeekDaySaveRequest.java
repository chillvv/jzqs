package com.jzqs.app.menu.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record MenuWeekDaySaveRequest(
    @Valid @NotNull MenuWeekDaySlotRequest lunch,
    @Valid @NotNull MenuWeekDaySlotRequest dinner
) {
}
