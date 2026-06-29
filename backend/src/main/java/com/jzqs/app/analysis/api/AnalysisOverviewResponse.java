package com.jzqs.app.analysis.api;

import java.math.BigDecimal;

public record AnalysisOverviewResponse(
    String date,
    BigDecimal totalSales,
    BigDecimal totalCost,
    BigDecimal totalProfit,
    int totalOrders,
    int totalMeals,
    int aftersaleCount
) {
}
