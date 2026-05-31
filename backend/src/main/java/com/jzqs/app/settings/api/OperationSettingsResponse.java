package com.jzqs.app.settings.api;
public record OperationSettingsResponse(
    boolean orderingEnabled,
    String orderingStatusLabel,
    String holidayNoticeTitle,
    String holidayNoticeDesc,
    String emergencyActionLabel,
    String bannerImages,
    boolean popupAnnouncementEnabled,
    String popupAnnouncementContent
) {
}
