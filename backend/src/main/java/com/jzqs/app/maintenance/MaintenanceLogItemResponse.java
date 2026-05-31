package com.jzqs.app.maintenance;

import java.time.LocalDateTime;

public record MaintenanceLogItemResponse(
    Long id,
    String jobType,
    String triggerSource,
    String status,
    String timeRangeLabel,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    Long durationMs,
    Integer scannedCount,
    Integer deletedCount,
    Integer failedCount,
    String message,
    String errorDetail
) {
}
