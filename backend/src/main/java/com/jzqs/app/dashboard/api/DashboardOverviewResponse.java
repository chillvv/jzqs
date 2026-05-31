package com.jzqs.app.dashboard.api;
import java.util.List;
public record DashboardOverviewResponse(
    int deliveredToday,
    int tomorrowMealCount,
    int tomorrowLunchCount,
    int tomorrowDinnerCount,
    int newCardsToday,
    int rechargeCustomersToday,
    int aftersaleToday,
    int cancellationsToday,
    int totalOrdersToday,
    int pendingOrdersToday,
    int pendingDispatchToday,
    int dispatchingOrdersToday,
    int deliveredOrdersToday,
    int lowBalanceCustomers,
    int openAftersaleCount,
    int specialOrdersToday,
    int menuRiskDays,
    List<OrderTrendPoint> orderTrend,
    List<GrowthTrendPoint> growthTrend
) {
    public record OrderTrendPoint(
        String label,
        int total,
        int lunch,
        int dinner
    ) {
    }

    public record GrowthTrendPoint(
        String label,
        int newCards,
        int recharges
    ) {
    }
}
