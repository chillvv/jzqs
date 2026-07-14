package com.jzqs.app.maintenance;

public record MaintenanceOverviewResponse(
    MaintenanceLogItemResponse latestManual,
    MaintenanceLogItemResponse latestAuto,
    MaintenanceLogItemResponse latestCloudReceipt,
    MaintenanceLogItemResponse latestCloudStorage,
    java.util.List<MaintenanceCleanupRuleResponse> cleanupRules,
    String nextAutoRunLabel
) {
}
