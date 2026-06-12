package com.jzqs.app.mobile.api;

import java.util.List;

public record MobileHomeResponse(
    long customerId,
    String name,
    String phone,
    String packageName,
    int totalMeals,
    int remainingMeals,
    boolean orderingEnabled,
    String orderingStatusLabel,
    String holidayNoticeTitle,
    String holidayNoticeDesc,
    String defaultAddress,
    String defaultUserRemark,
    String merchantRemark,
    List<MobileBannerItemResponse> bannerImages,
    int bannerIntervalMs,
    boolean popupAnnouncementEnabled,
    String popupAnnouncementContent
) {
}
