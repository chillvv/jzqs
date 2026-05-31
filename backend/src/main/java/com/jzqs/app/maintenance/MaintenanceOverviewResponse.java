package com.jzqs.app.maintenance;

public record MaintenanceOverviewResponse(
    MaintenanceLogItemResponse latestManual,
    MaintenanceLogItemResponse latestAuto,
    MaintenanceLogItemResponse latestCloudReceipt,
    MaintenanceLogItemResponse latestCloudStorage
) {
}
