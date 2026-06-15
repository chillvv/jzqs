package com.jzqs.app.settings.api;

import jakarta.validation.constraints.Min;

public record PackageReminderSettingsUpdateRequest(
    @Min(1) int packageExpiryReminderDays,
    @Min(1) int packageLowBalanceThreshold
) {
}
