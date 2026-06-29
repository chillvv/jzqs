package com.jzqs.app.maintenance;

import com.jzqs.app.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 回执图片清理接口（供云函数调用）
 * 这些接口仅供内部云函数使用，不对外暴露
 */
@RestController
@RequestMapping("/api/internal")
public class ReceiptCleanupController {

    private final ReceiptCleanupService receiptCleanupService;
    private final DataCleanupService dataCleanupService;

    public ReceiptCleanupController(ReceiptCleanupService receiptCleanupService, DataCleanupService dataCleanupService) {
        this.receiptCleanupService = receiptCleanupService;
        this.dataCleanupService = dataCleanupService;
    }

    /**
     * 获取需要从云存储删除的文件ID列表
     * 返回昨天及之前的、以 cloud:// 开头的回执文件ID
     */
    @GetMapping("/receipts/expired-file-ids")
    public ApiResponse<ExpiredReceiptFilesResponse> getExpiredFileIds() {
        return ApiResponse.success(receiptCleanupService.getExpiredFileIds());
    }

    /**
     * 标记文件已从云存储删除
     * 云函数删除成功后调用此接口，避免重复删除
     */
    @PostMapping("/receipts/mark-cloud-deleted")
    public ApiResponse<MarkCloudDeletedResponse> markCloudDeleted(
        @RequestBody MarkCloudDeletedRequest request
    ) {
        return ApiResponse.success(receiptCleanupService.markCloudDeleted(request));
    }

    @PostMapping("/maintenance/cloud-job-logs")
    public ApiResponse<MaintenanceLogItemResponse> reportCloudJob(
        @RequestBody CloudMaintenanceJobReportRequest request
    ) {
        return ApiResponse.success(dataCleanupService.recordCloudJob(request));
    }
}
