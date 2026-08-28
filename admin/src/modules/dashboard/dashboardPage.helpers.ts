import type { DashboardOverviewResponse } from "../../shared/api/types";

type DashboardOverviewLike = Partial<DashboardOverviewResponse>;

function toInt(value: number | string | null | undefined): number {
  if (value === null || value === undefined) {
    return 0;
  }
  const parsed = typeof value === "string" ? Number(value) : value;
  return Number.isFinite(parsed) ? Math.trunc(parsed) : 0;
}

export function normalizeDashboardOverview(data: DashboardOverviewLike): DashboardOverviewResponse {
  return {
    todayServeMealCount: toInt(data.todayServeMealCount),
    todayServeLunchCount: toInt(data.todayServeLunchCount),
    todayServeDinnerCount: toInt(data.todayServeDinnerCount),
    todayRechargedMeals: toInt(data.todayRechargedMeals),
    tomorrowMealCount: toInt(data.tomorrowMealCount),
    tomorrowLunchCount: toInt(data.tomorrowLunchCount),
    tomorrowDinnerCount: toInt(data.tomorrowDinnerCount),
    tomorrowCustomerCount: toInt(data.tomorrowCustomerCount),
    tomorrowFixedOrderCount: toInt(data.tomorrowFixedOrderCount),
    deliveredToday: toInt(data.deliveredToday),
    totalOrdersToday: toInt(data.totalOrdersToday),
    pendingOrdersToday: toInt(data.pendingOrdersToday),
    pendingDispatchToday: toInt(data.pendingDispatchToday),
    dispatchingOrdersToday: toInt(data.dispatchingOrdersToday),
    deliveredOrdersToday: toInt(data.deliveredOrdersToday ?? data.deliveredToday),
    newCardsToday: toInt(data.newCardsToday),
    rechargeCustomersToday: toInt(data.rechargeCustomersToday),
    aftersaleToday: toInt(data.aftersaleToday),
    cancellationsToday: toInt(data.cancellationsToday),
    lowBalanceCustomers: toInt(data.lowBalanceCustomers),
    expiringSoonCustomers: toInt(data.expiringSoonCustomers),
    openAftersaleCount: toInt(data.openAftersaleCount),
    menuRiskDays: toInt(data.menuRiskDays),
    orderTrend: Array.isArray(data.orderTrend) ? data.orderTrend : [],
    growthTrend: Array.isArray(data.growthTrend) ? data.growthTrend : []
  };
}

// 仅保留今日流转所需的 4 个 KPI，与用户截图完全一致
export type DashboardFlowStep = {
  index: number;          // 序号 (2~5)
  tone: "neutral" | "blue" | "emerald";
  label: string;
  value: number;          // 份数
  detail: string;         // 底部说明 (待人工确认/校对 ...)
  path?: string;
  // 完成率（仅送达这步使用）
  completionRate?: number | null;
};

export function buildDashboardFlowSteps(data: DashboardOverviewResponse): DashboardFlowStep[] {
  const totalServe = data.todayServeMealCount;
  const delivered = data.deliveredOrdersToday;
  const completionRate = totalServe > 0 ? Math.round((delivered / totalServe) * 100) : 0;

  return [
    {
      index: 2,
      tone: "neutral",
      label: "待处理",
      value: data.pendingOrdersToday,
      detail: "待人工确认/校核",
      path: "/orders"
    },
    {
      index: 3,
      tone: "blue",
      label: "待派单",
      value: data.pendingDispatchToday,
      detail: "已确认，待指派骑手",
      path: "/dispatch"
    },
    {
      index: 4,
      tone: "blue",
      label: "配送中",
      value: data.dispatchingOrdersToday,
      detail: "骑手履约中",
      path: "/dispatch"
    },
    {
      index: 5,
      tone: "emerald",
      label: "今日已送达",
      value: delivered,
      detail: totalServe > 0 ? `完成率 ${completionRate}%` : "等待今日首单送达",
      completionRate: totalServe > 0 ? completionRate : null,
      path: "/orders"
    }
  ];
}

// 订单趋势（7 天）摘要，紧凑展示用
export function buildDashboardOrderTrendSummary(data: DashboardOverviewResponse) {
  const orderTrend = data.orderTrend ?? [];
  const totals = orderTrend.map((item) => item.total);
  const lunches = orderTrend.map((item) => item.lunch);
  const dinners = orderTrend.map((item) => item.dinner);
  const peak = orderTrend.reduce(
    (current, item) => (item.total > current.total ? item : current),
    orderTrend[0] ?? { label: "-", total: 0, lunch: 0, dinner: 0 }
  );
  const totalSum = totals.reduce((sum, value) => sum + value, 0);
  const totalAverage = totals.length ? Math.round(totalSum / totals.length) : 0;
  const lunchTotal = lunches.reduce((sum, value) => sum + value, 0);
  const dinnerTotal = dinners.reduce((sum, value) => sum + value, 0);
  const lunchShare = lunchTotal + dinnerTotal === 0
    ? 0
    : Math.round((lunchTotal / (lunchTotal + dinnerTotal)) * 100);

  return {
    peakValue: peak.total,
    peakLabel: peak.label,
    averageValue: totalAverage,
    lunchShare,
    rangeText: totals.length ? `${Math.min(...totals)}-${Math.max(...totals)}` : "0-0",
    sum: totalSum
  };
}
