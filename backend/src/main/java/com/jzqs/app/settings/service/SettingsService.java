package com.jzqs.app.settings.service;

import com.jzqs.app.settings.api.OperationSettingsResponse;

public interface SettingsService {
    OperationSettingsResponse operationSettings();

    OperationSettingsResponse updateOrderingEnabled(boolean enabled);

    OperationSettingsResponse updateHolidayNotice(String title, String desc);

    OperationSettingsResponse updateBannerImages(String bannerImages);

    OperationSettingsResponse updatePopupAnnouncement(boolean enabled, String content);
}
