package com.jzqs.app.maintenance;

import com.jzqs.app.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 回执图片清理接口（供云函数调用）
 * 这些接口仅供内部云函数使用，不对外暴露
 */
@RestController
@RequestMapping("/api/internal")
public class ReceiptCleanupController {

    private final JdbcTemplate jdbcTemplate;
    private final DataCleanupService dataCleanupService;

    public ReceiptCleanupController(JdbcTemplate jdbcTemplate, DataCleanupService dataCleanupService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataCleanupService = dataCleanupService;
    }

    /**
     * 获取需要从云存储删除的文件ID列表
     * 返回昨天及之前的、以 cloud:// 开头的回执文件ID
     */
    @GetMapping("/receipts/expired-file-ids")
    public ApiResponse<Map<String, Object>> getExpiredFileIds() {
        // 截止时间：今天0点（即昨天及之前的所有记录）
        LocalDateTime cutoff = LocalDateTime.now().toLocalDate().atStartOfDay();

        List<String> fileIds = jdbcTemplate.queryForList(
            """
            SELECT receipt_url
            FROM delivery_receipts
            WHERE delivered_at < ?
              AND receipt_url LIKE 'cloud://%'
              AND (cloud_deleted IS NULL OR cloud_deleted = FALSE)
            LIMIT 500
            """,
            String.class,
            cutoff
        );

        return ApiResponse.success(Map.of(
            "fileIds", fileIds,
            "count", fileIds.size(),
            "cutoff", cutoff.toString()
        ));
    }

    /**
     * 标记文件已从云存储删除
     * 云函数删除成功后调用此接口，避免重复删除
     */
    @PostMapping("/receipts/mark-cloud-deleted")
    public ApiResponse<Map<String, Object>> markCloudDeleted(
        @RequestBody Map<String, List<String>> body
    ) {
        List<String> fileIds = body.get("fileIds");
        if (fileIds == null || fileIds.isEmpty()) {
            return ApiResponse.success(Map.of("updated", 0));
        }

        // 批量标记为已删除
        int updated = 0;
        for (String fileId : fileIds) {
            updated += jdbcTemplate.update(
                "UPDATE delivery_receipts SET cloud_deleted = TRUE WHERE receipt_url = ?",
                fileId
            );
        }

        return ApiResponse.success(Map.of(
            "updated", updated,
            "requested", fileIds.size()
        ));
    }

    @PostMapping("/maintenance/cloud-job-logs")
    public ApiResponse<MaintenanceLogItemResponse> reportCloudJob(
        @RequestBody CloudMaintenanceJobReportRequest request
    ) {
        return ApiResponse.success(dataCleanupService.recordCloudJob(request));
    }
}
