package com.jzqs.app.settings.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.annotation.RateLimit;
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
    @RateLimit(key = "admin:settings:ordering-toggle", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:settings:ordering-toggle", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "SETTINGS", action = "ORDERING_TOGGLE")
    public ApiResponse<OperationSettingsResponse> updateOrderingToggle(@Valid @RequestBody ToggleOrderingRequest request) {
        return ApiResponse.success(settingsService.updateOrderingEnabled(request.enabled()));
    }

    @PostMapping("/holiday-notice")
    @RateLimit(key = "admin:settings:holiday-notice", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:settings:holiday-notice", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "SETTINGS", action = "HOLIDAY_NOTICE")
    public ApiResponse<OperationSettingsResponse> updateHolidayNotice(@Valid @RequestBody HolidayNoticeUpdateRequest request) {
        return ApiResponse.success(settingsService.updateHolidayNotice(request.title(), request.description()));
    }

    @PostMapping("/banner-images")
    @RateLimit(key = "admin:settings:banner-images", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:settings:banner-images", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "SETTINGS", action = "BANNER_IMAGES")
    public ApiResponse<OperationSettingsResponse> updateBannerImages(@Valid @RequestBody BannerImagesUpdateRequest request) {
        return ApiResponse.success(settingsService.updateBannerImages(request.bannerImages(), request.bannerIntervalSeconds()));
    }

    @PostMapping(value = "/banner-images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BannerImageUploadResponse> uploadBannerImage(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(settingsService.uploadBannerImage(file));
    }

    @PostMapping("/ordering/pause-with-notice")
    @RateLimit(key = "admin:settings:pause-with-notice", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:settings:pause-with-notice", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "SETTINGS", action = "PAUSE_WITH_NOTICE")
    public ApiResponse<OperationSettingsResponse> pauseOrderingWithNotice(@Valid @RequestBody PauseOrderingWithNoticeRequest request) {
        return ApiResponse.success(settingsService.pauseOrderingWithNotice(
            request.title(),
            request.description(),
            request.popupEnabled(),
            request.popupContent()
        ));
    }

    @PostMapping("/popup-announcement")
    @RateLimit(key = "admin:settings:popup-announcement", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:settings:popup-announcement", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "SETTINGS", action = "POPUP_ANNOUNCEMENT")
    public ApiResponse<OperationSettingsResponse> updatePopupAnnouncement(@Valid @RequestBody PopupAnnouncementUpdateRequest request) {
        return ApiResponse.success(settingsService.updatePopupAnnouncement(
            request.title(),
            request.description(),
            request.enabled(),
            request.content()
        ));
    }

    @PostMapping("/package-reminders")
    @RateLimit(key = "admin:settings:package-reminders", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:settings:package-reminders", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "SETTINGS", action = "PACKAGE_REMINDERS")
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
