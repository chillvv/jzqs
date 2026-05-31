package com.jzqs.app.settings.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.settings.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return ApiResponse.success(settingsService.updateBannerImages(request.bannerImages()));
    }

    @PostMapping("/popup-announcement")
    public ApiResponse<OperationSettingsResponse> updatePopupAnnouncement(@Valid @RequestBody PopupAnnouncementUpdateRequest request) {
        return ApiResponse.success(settingsService.updatePopupAnnouncement(request.enabled(), request.content()));
    }
}
