package com.jzqs.app.maintenance;

import java.time.LocalDateTime;
import java.util.Map;

public record CloudMaintenanceJobReportRequest(
    String jobType,
    String status,
    String timeRangeLabel,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    Integer scannedCount,
    Integer deletedCount,
    Integer failedCount,
    String message,
    String errorDetail,
    Map<String, Object> metadata
) {
}
