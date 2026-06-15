package com.jzqs.app.settings.api;
public record OperationSettingsResponse(
    boolean orderingEnabled,
    String orderingStatusLabel,
    String holidayNoticeTitle,
    String holidayNoticeDesc,
    String emergencyActionLabel,
    String bannerImages,
    int bannerIntervalSeconds,
    int packageExpiryReminderDays,
    int packageLowBalanceThreshold,
    boolean popupAnnouncementEnabled,
    String popupAnnouncementContent
) {
}
