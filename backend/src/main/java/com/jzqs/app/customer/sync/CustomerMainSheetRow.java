package com.jzqs.app.customer.sync;

import java.time.LocalDate;

public record CustomerMainSheetRow(
    String name,
    String phone,
    LocalDate openedAt,
    LocalDate expiredAt,
    int remainingMeals
) {
}
