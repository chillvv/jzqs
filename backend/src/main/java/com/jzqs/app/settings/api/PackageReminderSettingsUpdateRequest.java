package com.jzqs.app.settings.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PackageReminderSettingsUpdateRequest(
    @Min(1) int packageExpiryReminderDays,
    @Min(1) int packageLowBalanceThreshold,
    boolean mealReminderPopupEnabled,
    boolean deliverySubscribeEnabled,
    @NotBlank(message = "deliverySubscribeLunchTime is required")
    String deliverySubscribeLunchTime,
    @NotBlank(message = "deliverySubscribeDinnerTime is required")
    String deliverySubscribeDinnerTime
) {
}
