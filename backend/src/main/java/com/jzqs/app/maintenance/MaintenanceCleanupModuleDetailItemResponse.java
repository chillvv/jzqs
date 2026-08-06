package com.jzqs.app.maintenance;

/**
 * 单次清理任务中某个具体数据来源的明细记录。
 * 颗粒度精确到数据表/落盘文件，用于在维护日志详情里还原"清理了什么、清了多少"。
 */
public record MaintenanceCleanupModuleDetailItemResponse(
    String scopeLabel,
    String tableName,
    int scannedCount,
    int deletedCount,
    int failedCount,
    String rangeLabel,
    String note
) {
}
