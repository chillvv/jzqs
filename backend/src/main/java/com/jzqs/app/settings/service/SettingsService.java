package com.jzqs.app.settings.service;

import com.jzqs.app.settings.api.BannerImageUploadResponse;
import com.jzqs.app.settings.api.DispatchAreaCodeListResponse;
import com.jzqs.app.settings.api.DispatchAreaMemoryListResponse;
import com.jzqs.app.settings.api.DispatchAreaMemorySourceListResponse;
import com.jzqs.app.settings.api.DispatchAreaMemoryUpdateRequest;
import com.jzqs.app.settings.api.DispatchAiRunNowResponse;
import com.jzqs.app.settings.api.DispatchAiWorkbenchResponse;
import com.jzqs.app.settings.api.OperationSettingsResponse;
import org.springframework.web.multipart.MultipartFile;
public interface SettingsService {
    OperationSettingsResponse operationSettings();

    DispatchAiWorkbenchResponse dispatchAiWorkbench();

    DispatchAiWorkbenchResponse updateDispatchRouteWorkbenchSettings(
        boolean autoScheduleEnabled,
        String autoScheduleTime,
        String defaultStrategyMode,
        String anchorAddress,
        String updatedBy
    );

    DispatchAiWorkbenchResponse updateDispatchAiSettings(
        boolean autoScheduleEnabled,
        String autoScheduleTime,
        String defaultStrategyMode,
        String anchorName,
        String anchorAddress,
        boolean aiEnabled,
        String apiBaseUrl,
        String apiKey,
        String aiModel,
        String aiPromptTemplate,
        String lowBalanceThreshold,
        String updatedBy
    );

    DispatchAiWorkbenchResponse refreshDispatchAiBalance(String updatedBy);

    DispatchAreaCodeListResponse listDispatchAreaCodes();

    DispatchAreaMemoryListResponse listDispatchAreaMemories(String areaCode);

    DispatchAreaMemorySourceListResponse getDispatchAreaMemorySources(long memoryId);

    DispatchAreaMemoryListResponse updateDispatchAreaMemory(long memoryId, DispatchAreaMemoryUpdateRequest request, String updatedBy);

    DispatchAreaMemoryListResponse deleteDispatchAreaMemory(long memoryId, String updatedBy);

    DispatchAiRunNowResponse runDispatchAiNow(String serveDate, String mealPeriod, String areaCode, String operatorName);

    void runDispatchAiAutoSchedule();

    OperationSettingsResponse updateOrderingEnabled(boolean enabled);

    OperationSettingsResponse updateHolidayNotice(String title, String desc);

    OperationSettingsResponse updateBannerImages(String bannerImages);

    OperationSettingsResponse updateBannerImages(String bannerImages, int bannerIntervalSeconds);

    OperationSettingsResponse updatePopupAnnouncement(String title, String desc, boolean enabled, String content);

    OperationSettingsResponse updatePackageReminderSettings(
        int packageExpiryReminderDays,
        int packageLowBalanceThreshold,
        boolean mealReminderPopupEnabled,
        boolean deliverySubscribeEnabled,
        String deliverySubscribeLunchTime,
        String deliverySubscribeDinnerTime
    );

    BannerImageUploadResponse uploadBannerImage(MultipartFile file);

    OperationSettingsResponse pauseOrderingWithNotice(String title, String desc, boolean popupEnabled, String popupContent);
}
