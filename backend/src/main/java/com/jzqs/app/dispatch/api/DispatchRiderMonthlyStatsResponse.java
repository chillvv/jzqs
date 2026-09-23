package com.jzqs.app.dispatch.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 骑手月度配送人工成本统计。
 * 单量口径：dispatch_assignments.status = 'DELIVERED' 的餐段订单数，按 daily_orders.serve_date 落在目标月份
 * （取消/退款的订单派单行已删除，天然不计入）。
 *
 * @param totalDeliveredCount      当月全部已送达单量（占比分母）
 * @param unassignedDeliveredCount 未归属到任何骑手档案的历史单量，用于解释占比之和不足 100% 的部分
 * @param totalMonthlyCost         已设置月薪的骑手月薪合计（元）
 * @param averageCostPerOrder      整体单均人工成本（月薪合计 ÷ 当月总单量，保留 2 位小数）；无月薪或无单时为 null
 */
public record DispatchRiderMonthlyStatsResponse(
    String month,
    int totalDeliveredCount,
    int unassignedDeliveredCount,
    BigDecimal totalMonthlyCost,
    BigDecimal averageCostPerOrder,
    List<DispatchRiderMonthlyStatItem> riders
) {
}
