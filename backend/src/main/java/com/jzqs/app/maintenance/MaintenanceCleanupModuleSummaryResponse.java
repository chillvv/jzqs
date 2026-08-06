package com.jzqs.app.maintenance;

import java.util.List;

public record MaintenanceCleanupModuleSummaryResponse(
    String moduleKey,
    String moduleLabel,
    int scannedCount,
    int deletedCount,
    int failedCount,
    String timeRangeLabel,
    String summary,
    List<MaintenanceCleanupModuleDetailItemResponse> details
) {
}
