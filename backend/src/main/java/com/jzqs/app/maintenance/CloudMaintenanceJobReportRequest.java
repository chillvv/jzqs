package com.jzqs.app.maintenance;

import java.time.LocalDateTime;

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
    CloudMaintenanceJobMetadataRequest metadata
) {
}
