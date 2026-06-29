package com.jzqs.app.maintenance;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.annotation.RateLimit;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统维护接口
 */
@RestController
@RequestMapping("/api/admin/maintenance")
public class MaintenanceController {
    
    private final DataCleanupService dataCleanupService;
    
    public MaintenanceController(DataCleanupService dataCleanupService) {
        this.dataCleanupService = dataCleanupService;
    }
    
    /**
     * 手动触发数据清理
     */
    @PostMapping("/cleanup")
    @RateLimit(key = "admin:maintenance:cleanup", maxRequests = 2, windowSeconds = 20)
    @Idempotent(key = "admin:maintenance:cleanup", ttlSeconds = 10, includeBody = false)
    @AuditAction(module = "MAINTENANCE", action = "TRIGGER_CLEANUP")
    public ApiResponse<Map<String, String>> triggerCleanup() {
        String message = dataCleanupService.manualCleanup();
        return ApiResponse.success(Map.of(
            "status", "SUCCESS",
            "message", message
        ));
    }

    @PostMapping("/cleanup/{moduleKey}")
    @RateLimit(key = "admin:maintenance:cleanup-module", maxRequests = 3, windowSeconds = 20)
    @Idempotent(key = "admin:maintenance:cleanup-module", ttlSeconds = 10, includeBody = false)
    @AuditAction(module = "MAINTENANCE", action = "TRIGGER_MODULE_CLEANUP")
    public ApiResponse<Map<String, String>> triggerCleanupByModule(@PathVariable String moduleKey) {
        String message = dataCleanupService.manualCleanupByModule(moduleKey);
        return ApiResponse.success(Map.of(
            "status", "SUCCESS",
            "message", message
        ));
    }

    @PostMapping("/settings")
    @RateLimit(key = "admin:maintenance:settings", maxRequests = 4, windowSeconds = 15)
    @Idempotent(key = "admin:maintenance:settings", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "MAINTENANCE", action = "UPDATE_SETTINGS")
    public ApiResponse<MaintenanceOverviewResponse> updateCleanupSettings(
        @RequestBody MaintenanceCleanupSettingsUpdateRequest request
    ) {
        return ApiResponse.success(dataCleanupService.updateCleanupSettings(request));
    }

    @GetMapping("/overview")
    public ApiResponse<MaintenanceOverviewResponse> getOverview() {
        return ApiResponse.success(dataCleanupService.fetchOverview());
    }

    @GetMapping("/logs")
    public ApiResponse<List<MaintenanceLogItemResponse>> getLogs() {
        return ApiResponse.success(dataCleanupService.fetchRecentLogs(20));
    }
}
