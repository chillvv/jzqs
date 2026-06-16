import type { DashboardOverviewResponse } from "../../shared/api/types";

type DashboardOverviewLike = Partial<DashboardOverviewResponse>;

export function normalizeDashboardOverview(data: DashboardOverviewLike): DashboardOverviewResponse {
  return {
    deliveredToday: data.deliveredToday ?? 0,
    tomorrowMealCount: data.tomorrowMealCount ?? 0,
    tomorrowLunchCount: data.tomorrowLunchCount ?? 0,
    tomorrowDinnerCount: data.tomorrowDinnerCount ?? 0,
    newCardsToday: data.newCardsToday ?? 0,
    rechargeCustomersToday: data.rechargeCustomersToday ?? 0,
    aftersaleToday: data.aftersaleToday ?? 0,
    cancellationsToday: data.cancellationsToday ?? 0,
    totalOrdersToday: data.totalOrdersToday ?? 0,
    pendingOrdersToday: data.pendingOrdersToday ?? 0,
    pendingDispatchToday: data.pendingDispatchToday ?? 0,
    dispatchingOrdersToday: data.dispatchingOrdersToday ?? 0,
    deliveredOrdersToday: data.deliveredOrdersToday ?? data.deliveredToday ?? 0,
    lowBalanceCustomers: data.lowBalanceCustomers ?? 0,
    expiringSoonCustomers: data.expiringSoonCustomers ?? 0,
    openAftersaleCount: data.openAftersaleCount ?? 0,
    menuRiskDays: data.menuRiskDays ?? 0,
    orderTrend: Array.isArray(data.orderTrend) ? data.orderTrend : [],
    growthTrend: Array.isArray(data.growthTrend) ? data.growthTrend : []
  };
}

export function buildDashboardHeroMetrics(data: DashboardOverviewResponse) {
  return [
    {
      label: "今日送达",
      value: data.deliveredToday,
      unit: "份",
      tone: "blue",
      detail: "按送达回执"
    },
    {
      label: "明日订单",
      value: data.tomorrowMealCount,
      unit: "份",
      tone: "cyan",
      detail: `午餐 ${data.tomorrowLunchCount} / 晚餐 ${data.tomorrowDinnerCount}`
    },
    {
      label: "今日新开卡",
      value: data.newCardsToday,
      unit: "人",
      tone: "emerald",
      detail: "首开与建档"
    },
    {
      label: "今日续卡充值",
      value: data.rechargeCustomersToday,
      unit: "人",
      tone: "violet",
      detail: "续卡与补餐"
    },
    {
      label: "今日售后",
      value: data.aftersaleToday,
      unit: "单",
      tone: "amber",
      detail: "新增与处理中"
    },
    {
      label: "今日取消",
      value: data.cancellationsToday,
      unit: "单",
      tone: "red",
      detail: "当日取消单"
    }
  ];
}

export function buildDashboardProgressItems(data: DashboardOverviewResponse) {
  return [
    { label: "今日总订单", value: data.totalOrdersToday, tone: "blue", detail: "全部进入流程的订单" },
    { label: "待处理", value: data.pendingOrdersToday, tone: "amber", detail: "待人工继续流转" },
    { label: "待派单", value: data.pendingDispatchToday, tone: "violet", detail: "已确认但未指派骑手" },
    { label: "配送中", value: data.dispatchingOrdersToday, tone: "blue", detail: "骑手已接单履约中" },
    { label: "已送达", value: data.deliveredOrdersToday, tone: "emerald", detail: "当前已完成闭环" }
  ];
}

export function buildDashboardExceptionItems(data: DashboardOverviewResponse) {
  return [
    { label: "低余额客户", value: data.lowBalanceCustomers, tone: "amber", detail: "余额低于阈值的客户" },
    { label: "即将到期", value: data.expiringSoonCustomers, tone: "violet", detail: "餐包即将到期客户" },
    { label: "待处理售后", value: data.openAftersaleCount, tone: "red", detail: "待闭环售后单" },
    { label: "菜单配置风险", value: data.menuRiskDays, tone: "blue", detail: "未来 7 天未配餐槽" }
  ];
}

export function buildDashboardOrderTrendSummary(data: DashboardOverviewResponse) {
  const orderTrend = data.orderTrend ?? [];
  const totals = orderTrend.map((item) => item.total);
  const lunches = orderTrend.map((item) => item.lunch);
  const dinners = orderTrend.map((item) => item.dinner);
  const peak = orderTrend.reduce(
    (current, item) => (item.total > current.total ? item : current),
    orderTrend[0] ?? { label: "-", total: 0, lunch: 0, dinner: 0 }
  );
  const totalAverage = totals.length ? Math.round(totals.reduce((sum, value) => sum + value, 0) / totals.length) : 0;
  const lunchTotal = lunches.reduce((sum, value) => sum + value, 0);
  const dinnerTotal = dinners.reduce((sum, value) => sum + value, 0);
  const lunchShare = lunchTotal + dinnerTotal === 0 ? 0 : Math.round((lunchTotal / (lunchTotal + dinnerTotal)) * 100);

  return {
    peakValue: peak.total,
    peakLabel: peak.label,
    averageValue: totalAverage,
    lunchShare,
    rangeText: totals.length ? `${Math.min(...totals)}-${Math.max(...totals)}` : "0-0"
  };
}
