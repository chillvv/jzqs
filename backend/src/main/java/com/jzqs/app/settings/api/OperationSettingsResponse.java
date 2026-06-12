package com.jzqs.app.settings.api;
public record OperationSettingsResponse(
    boolean orderingEnabled,
    String orderingStatusLabel,
    String holidayNoticeTitle,
    String holidayNoticeDesc,
    String emergencyActionLabel,
    String bannerImages,
    int bannerIntervalSeconds,
    boolean popupAnnouncementEnabled,
    String popupAnnouncementContent
) {
}
