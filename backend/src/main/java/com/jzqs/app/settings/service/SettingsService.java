package com.jzqs.app.settings.service;

import com.jzqs.app.settings.api.BannerImageUploadResponse;
import com.jzqs.app.settings.api.OperationSettingsResponse;
import org.springframework.web.multipart.MultipartFile;
public interface SettingsService {
    OperationSettingsResponse operationSettings();

    OperationSettingsResponse updateOrderingEnabled(boolean enabled);

    OperationSettingsResponse updateHolidayNotice(String title, String desc);

    OperationSettingsResponse updateBannerImages(String bannerImages);

    OperationSettingsResponse updatePopupAnnouncement(boolean enabled, String content);

    BannerImageUploadResponse uploadBannerImage(MultipartFile file);

    OperationSettingsResponse pauseOrderingWithNotice(String title, String desc, boolean popupEnabled, String popupContent);
}
