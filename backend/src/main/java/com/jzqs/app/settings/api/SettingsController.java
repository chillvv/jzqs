package com.jzqs.app.settings.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.settings.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {
    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/operation-status")
    public ApiResponse<OperationSettingsResponse> operationStatus() {
        return ApiResponse.success(settingsService.operationSettings());
    }

    @PostMapping("/ordering-toggle")
    public ApiResponse<OperationSettingsResponse> updateOrderingToggle(@Valid @RequestBody ToggleOrderingRequest request) {
        return ApiResponse.success(settingsService.updateOrderingEnabled(request.enabled()));
    }

    @PostMapping("/holiday-notice")
    public ApiResponse<OperationSettingsResponse> updateHolidayNotice(@Valid @RequestBody HolidayNoticeUpdateRequest request) {
        return ApiResponse.success(settingsService.updateHolidayNotice(request.title(), request.description()));
    }

    @PostMapping("/banner-images")
    public ApiResponse<OperationSettingsResponse> updateBannerImages(@Valid @RequestBody BannerImagesUpdateRequest request) {
        return ApiResponse.success(settingsService.updateBannerImages(request.bannerImages(), request.bannerIntervalSeconds()));
    }

    @PostMapping(value = "/banner-images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BannerImageUploadResponse> uploadBannerImage(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(settingsService.uploadBannerImage(file));
    }

    @PostMapping("/ordering/pause-with-notice")
    public ApiResponse<OperationSettingsResponse> pauseOrderingWithNotice(@Valid @RequestBody PauseOrderingWithNoticeRequest request) {
        return ApiResponse.success(settingsService.pauseOrderingWithNotice(
            request.title(),
            request.description(),
            request.popupEnabled(),
            request.popupContent()
        ));
    }

    @PostMapping("/popup-announcement")
    public ApiResponse<OperationSettingsResponse> updatePopupAnnouncement(@Valid @RequestBody PopupAnnouncementUpdateRequest request) {
        return ApiResponse.success(settingsService.updatePopupAnnouncement(
            request.title(),
            request.description(),
            request.enabled(),
            request.content()
        ));
    }

    @PostMapping("/package-reminders")
    public ApiResponse<OperationSettingsResponse> updatePackageReminders(@Valid @RequestBody PackageReminderSettingsUpdateRequest request) {
        return ApiResponse.success(settingsService.updatePackageReminderSettings(
            request.packageExpiryReminderDays(),
            request.packageLowBalanceThreshold(),
            request.mealReminderPopupEnabled(),
            request.deliverySubscribeEnabled(),
            request.deliverySubscribeLunchTime(),
            request.deliverySubscribeDinnerTime()
        ));
    }
}
