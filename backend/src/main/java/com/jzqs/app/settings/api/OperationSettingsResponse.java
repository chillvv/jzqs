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
    boolean mealReminderPopupEnabled,
    boolean deliverySubscribeEnabled,
    String deliverySubscribeLunchTime,
    String deliverySubscribeDinnerTime,
    boolean nightlyReminderEnabled,
    String nightlyReminderTime,
    String nightlyReminderDescription,
    String nightlyReminderTip,
    boolean popupAnnouncementEnabled,
    String popupAnnouncementContent,
    String restNoticeTemplate
) {
}
