package com.jzqs.app.maintenance;

import java.util.List;

public record MaintenanceCleanupSettingsUpdateRequest(
    List<MaintenanceCleanupRuleItem> rules
) {
    public record MaintenanceCleanupRuleItem(
        String moduleKey,
        Integer retentionValue,
        String retentionUnit,
        Boolean autoEnabled
    ) {
    }
}
