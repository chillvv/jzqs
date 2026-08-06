package com.jzqs.app.customer.api;

/**
 * 批量统一延长有效期结果。
 *
 * @param affectedCount 实际延期的客户数（未过期）
 * @param skippedCount  跳过的客户数（无到期时间或已过期）
 * @param totalCount    参与扫描的活跃钱包总数
 */
public record CustomerBatchExtendResponse(
    int affectedCount,
    int skippedCount,
    int totalCount
) {
}
