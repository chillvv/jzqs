package com.jzqs.app.maintenance;

public record MaintenanceCleanupModuleSummaryResponse(
    String moduleKey,
    String moduleLabel,
    int scannedCount,
    int deletedCount,
    int failedCount,
    String timeRangeLabel,
    String summary
) {
}
