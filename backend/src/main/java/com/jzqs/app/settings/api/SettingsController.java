package com.jzqs.app.settings.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.annotation.RateLimit;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import com.jzqs.app.settings.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/dispatch-ai-workbench")
    public ApiResponse<DispatchAiWorkbenchResponse> dispatchAiWorkbench() {
        return ApiResponse.success(settingsService.dispatchAiWorkbench());
    }

    @PostMapping("/dispatch-route-workbench")
    @RateLimit(key = "admin:settings:dispatch-route-workbench", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:settings:dispatch-route-workbench", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "SETTINGS", action = "DISPATCH_ROUTE_WORKBENCH")
    public ApiResponse<DispatchAiWorkbenchResponse> updateDispatchRouteWorkbench(@Valid @RequestBody DispatchRouteWorkbenchSettingsUpdateRequest request) {
        return ApiResponse.success(settingsService.updateDispatchRouteWorkbenchSettings(
            request.autoScheduleEnabled(),
            request.autoScheduleTime(),
            request.defaultStrategyMode(),
            request.anchorAddress(),
            AdminRequestContextSupport.requireOperatorName()
        ));
    }

    @PostMapping("/dispatch-ai-workbench")
    @RateLimit(key = "admin:settings:dispatch-ai-workbench", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:settings:dispatch-ai-workbench", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "SETTINGS", action = "DISPATCH_AI_SETTINGS")
    public ApiResponse<DispatchAiWorkbenchResponse> updateDispatchAiWorkbench(@Valid @RequestBody DispatchAiSettingsUpdateRequest request) {
        return ApiResponse.success(settingsService.updateDispatchAiSettings(
            request.autoScheduleEnabled(),
            request.autoScheduleTime(),
            request.defaultStrategyMode(),
            request.anchorName(),
            request.anchorAddress(),
            request.aiEnabled(),
            request.apiBaseUrl(),
            request.apiKey(),
            request.aiModel(),
            request.aiPromptTemplate(),
            request.lowBalanceThreshold(),
            AdminRequestContextSupport.requireOperatorName()
        ));
    }

    @PostMapping("/dispatch-ai-workbench/refresh-balance")
    @RateLimit(key = "admin:settings:dispatch-ai-balance", maxRequests = 6, windowSeconds = 60)
    @AuditAction(module = "SETTINGS", action = "DISPATCH_AI_BALANCE_REFRESH")
    public ApiResponse<DispatchAiWorkbenchResponse> refreshDispatchAiBalance() {
        return ApiResponse.success(settingsService.refreshDispatchAiBalance(AdminRequestContextSupport.requireOperatorName()));
    }

    @GetMapping("/dispatch-area-codes")
    public ApiResponse<DispatchAreaCodeListResponse> listDispatchAreaCodes() {
        return ApiResponse.success(settingsService.listDispatchAreaCodes());
    }

    @GetMapping("/dispatch-area-memories")
    public ApiResponse<DispatchAreaMemoryListResponse> listDispatchAreaMemories(@RequestParam String areaCode) {
        return ApiResponse.success(settingsService.listDispatchAreaMemories(areaCode));
    }

    @GetMapping("/dispatch-area-memories/{memoryId}/sources")
    public ApiResponse<DispatchAreaMemorySourceListResponse> getDispatchAreaMemorySources(@PathVariable long memoryId) {
        return ApiResponse.success(settingsService.getDispatchAreaMemorySources(memoryId));
    }

    @PostMapping("/dispatch-area-memories/{memoryId}")
    @RateLimit(key = "admin:settings:dispatch-area-memory-update", maxRequests = 6, windowSeconds = 30)
    @Idempotent(key = "admin:settings:dispatch-area-memory-update", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "SETTINGS", action = "DISPATCH_AREA_MEMORY_UPDATE")
    public ApiResponse<DispatchAreaMemoryListResponse> updateDispatchAreaMemory(
        @PathVariable long memoryId,
        @Valid @RequestBody DispatchAreaMemoryUpdateRequest request
    ) {
        return ApiResponse.success(settingsService.updateDispatchAreaMemory(
            memoryId,
            request,
            AdminRequestContextSupport.requireOperatorName()
        ));
    }

    @PostMapping("/dispatch-area-memories/{memoryId}/delete")
    @RateLimit(key = "admin:settings:dispatch-area-memory-delete", maxRequests = 6, windowSeconds = 30)
    @Idempotent(key = "admin:settings:dispatch-area-memory-delete", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "SETTINGS", action = "DISPATCH_AREA_MEMORY_DELETE")
    public ApiResponse<DispatchAreaMemoryListResponse> deleteDispatchAreaMemory(@PathVariable long memoryId) {
        return ApiResponse.success(settingsService.deleteDispatchAreaMemory(
            memoryId,
            AdminRequestContextSupport.requireOperatorName()
        ));
    }

    @PostMapping("/dispatch-ai-workbench/run-now")
    @RateLimit(key = "admin:settings:dispatch-ai-run-now", maxRequests = 4, windowSeconds = 30)
    @AuditAction(module = "SETTINGS", action = "DISPATCH_AI_RUN_NOW")
    public ApiResponse<DispatchAiRunNowResponse> runDispatchAiNow(@RequestBody(required = false) DispatchAiRunNowRequest request) {
        DispatchAiRunNowRequest payload = request == null ? new DispatchAiRunNowRequest(null, null, null) : request;
        return ApiResponse.success(settingsService.runDispatchAiNow(
            payload.serveDate(),
            payload.mealPeriod(),
            payload.areaCode(),
            AdminRequestContextSupport.requireOperatorName()
        ));
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
