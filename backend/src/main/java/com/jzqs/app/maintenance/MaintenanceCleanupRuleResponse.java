package com.jzqs.app.maintenance;

public record MaintenanceCleanupRuleResponse(
    String moduleKey,
    String moduleLabel,
    int retentionValue,
    String retentionUnit,
    boolean autoEnabled,
    String lastResultSummary,
    String lastRunAt,
    String lastStatus
) {
}
