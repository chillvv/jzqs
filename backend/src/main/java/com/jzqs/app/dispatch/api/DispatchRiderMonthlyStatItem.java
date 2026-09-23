package com.jzqs.app.dispatch.api;

import java.math.BigDecimal;

/**
 * 骑手月度统计中的单个骑手。
 *
 * @param deliveredCount 当月已送达单量（一个餐段订单 = 1 单）
 * @param sharePercent   单量占比（%，以当月全部已送达单量为分母，保留 1 位小数）
 * @param monthlySalary  月薪（元），未设置为 null
 * @param costPerOrder   单均人工成本（月薪 ÷ 当月单量，保留 2 位小数）；月薪未设置或当月无单时为 null
 */
public record DispatchRiderMonthlyStatItem(
    long riderId,
    String riderName,
    String areaCode,
    String authStatus,
    int deliveredCount,
    BigDecimal sharePercent,
    BigDecimal monthlySalary,
    BigDecimal costPerOrder
) {
}
