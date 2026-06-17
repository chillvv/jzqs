package com.jzqs.app.maintenance;

import com.jzqs.app.common.api.ApiResponse;
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
    public ApiResponse<Map<String, String>> triggerCleanup() {
        String message = dataCleanupService.manualCleanup();
        return ApiResponse.success(Map.of(
            "status", "SUCCESS",
            "message", message
        ));
    }

    @PostMapping("/cleanup/{moduleKey}")
    public ApiResponse<Map<String, String>> triggerCleanupByModule(@PathVariable String moduleKey) {
        String message = dataCleanupService.manualCleanupByModule(moduleKey);
        return ApiResponse.success(Map.of(
            "status", "SUCCESS",
            "message", message
        ));
    }

    @PostMapping("/settings")
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
