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
    // 今日（按出餐日 serve_date 口径）
    todayServeMealCount: toInt(data.todayServeMealCount),
    todayServeLunchCount: toInt(data.todayServeLunchCount),
    todayServeDinnerCount: toInt(data.todayServeDinnerCount),
    todayRechargedMeals: toInt(data.todayRechargedMeals),
    // 明日（按明日 serve_date 口径）
    tomorrowMealCount: toInt(data.tomorrowMealCount),
    tomorrowLunchCount: toInt(data.tomorrowLunchCount),
    tomorrowDinnerCount: toInt(data.tomorrowDinnerCount),
    tomorrowCustomerCount: toInt(data.tomorrowCustomerCount),
    tomorrowFixedOrderCount: toInt(data.tomorrowFixedOrderCount),
    // 历史口径（兼容字段）
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
    // 待办
    lowBalanceCustomers: toInt(data.lowBalanceCustomers),
    expiringSoonCustomers: toInt(data.expiringSoonCustomers),
    openAftersaleCount: toInt(data.openAftersaleCount),
    menuRiskDays: toInt(data.menuRiskDays),
    orderTrend: Array.isArray(data.orderTrend) ? data.orderTrend : [],
    growthTrend: Array.isArray(data.growthTrend) ? data.growthTrend : []
  };
}

export type DashboardActionKey =
  | "今日出餐"
  | "明日出餐"
  | "今日已送达"
  | "今日新增销售"
  | "今日新开卡"
  | "今日售后"
  | "待处理"
  | "待派单"
  | "配送中"
  | "明日午餐"
  | "明日晚餐"
  | "明日客户"
  | "明日固定订餐"
  | "低余额客户"
  | "即将到期"
  | "待处理售后"
  | "菜单配置风险"
  | "今日取消";

export type DashboardMetricCard = {
  key: DashboardActionKey;
  label: string;
  value: number;
  unit: string;
  tone: "blue" | "cyan" | "emerald" | "violet" | "amber" | "red";
  detail: string;
  /** "今日" / "明日" / "近7天" —— 决定排序分组 */
  scope: "today" | "tomorrow" | "trend";
  /** 跳转路径 */
  path?: string;
};

/**
 * 顶部 4 个核心指标卡片：
 * - 商家一眼看到"今天要做什么 / 明天要备多少"
 * - 不再使用"近 7 天峰值/均值/占比/区间"作为顶部 —— 那些是次要趋势数据
 */
export function buildDashboardHeroMetrics(data: DashboardOverviewResponse): DashboardMetricCard[] {
  return [
    {
      key: "今日出餐",
      label: "今日出餐",
      value: data.todayServeMealCount,
      unit: "份",
      tone: "blue",
      detail: `午餐 ${data.todayServeLunchCount} / 晚餐 ${data.todayServeDinnerCount}`,
      scope: "today",
      path: "/orders"
    },
    {
      key: "明日出餐",
      label: "明日出餐",
      value: data.tomorrowMealCount,
      unit: "份",
      tone: "cyan",
      detail: `午餐 ${data.tomorrowLunchCount} / 晚餐 ${data.tomorrowDinnerCount}`,
      scope: "tomorrow",
      path: "/orders"
    },
    {
      key: "今日已送达",
      label: "今日已送达",
      value: data.deliveredOrdersToday,
      unit: "份",
      tone: "emerald",
      detail: "今日出餐且已送达",
      scope: "today",
      path: "/orders"
    },
    {
      key: "今日新增销售",
      label: "今日新增销售",
      value: data.todayRechargedMeals,
      unit: "餐",
      tone: "violet",
      detail: "今日开卡 + 续卡餐数",
      scope: "today",
      path: "/customers"
    }
  ];
}

/**
 * 待办与提醒（合并原"异常面板"，移除对商家无意义的"低余额客户数"等纯统计项），
 * 每条都标注"去处理 / 查看明细"的跳转路径。
 */
export function buildDashboardTodoItems(data: DashboardOverviewResponse) {
  return [
    {
      key: "待处理售后" as DashboardActionKey,
      label: "待处理售后",
      value: data.openAftersaleCount,
      tone: "red" as const,
      detail: "未闭环的售后工单",
      path: "/aftersales"
    },
    {
      key: "低余额客户" as DashboardActionKey,
      label: "低余额客户",
      value: data.lowBalanceCustomers,
      tone: "amber" as const,
      detail: "剩余餐数 ≤ 阈值，需提醒续费",
      path: "/customers"
    },
    {
      key: "即将到期" as DashboardActionKey,
      label: "餐包即将到期",
      value: data.expiringSoonCustomers,
      tone: "violet" as const,
      detail: "建议主动提醒客户续费",
      path: "/customers"
    },
    {
      key: "菜单配置风险" as DashboardActionKey,
      label: "菜单待配置",
      value: data.menuRiskDays,
      tone: "blue" as const,
      detail: "未来 7 天未配餐的天数",
      path: "/menu"
    }
  ];
}

/**
 * 今日订单流转（按口径标注，区分订单创建时间和出餐时间）
 */
export function buildDashboardProgressItems(data: DashboardOverviewResponse) {
  const totalServe = data.todayServeMealCount;
  const delivered = data.deliveredOrdersToday;
  return [
    {
      key: "今日总订单" as DashboardActionKey,
      label: "今日总订单",
      value: totalServe,
      tone: "blue" as const,
      detail: "今日所有需配送的订单份数",
      path: "/orders"
    },
    {
      key: "待处理" as DashboardActionKey,
      label: "待处理",
      value: data.pendingOrdersToday,
      tone: "amber" as const,
      detail: "待人工确认/校核",
      path: "/orders"
    },
    {
      key: "待派单" as DashboardActionKey,
      label: "待派单",
      value: data.pendingDispatchToday,
      tone: "violet" as const,
      detail: "已确认，待指派骑手",
      path: "/dispatch"
    },
    {
      key: "配送中" as DashboardActionKey,
      label: "配送中",
      value: data.dispatchingOrdersToday,
      tone: "blue" as const,
      detail: "骑手履约中",
      path: "/dispatch"
    },
    {
      key: "今日已送达" as DashboardActionKey,
      label: "今日已送达",
      value: delivered,
      tone: "emerald" as const,
      detail: totalServe > 0 ? `完成率 ${Math.round((delivered / totalServe) * 100)}%` : "等待今日首单送达",
      path: "/orders"
    }
  ];
}

/**
 * 明日备餐面板所需的数据：从订单口径到业务口径聚合
 */
export function buildDashboardTomorrowSummary(data: DashboardOverviewResponse) {
  const meals = data.tomorrowMealCount;
  const lunches = data.tomorrowLunchCount;
  const dinners = data.tomorrowDinnerCount;
  const customers = data.tomorrowCustomerCount;
  const fixed = data.tomorrowFixedOrderCount;
  const fixedShare = customers > 0 ? Math.round((fixed / customers) * 100) : 0;
  return {
    meals,
    lunches,
    dinners,
    customers,
    fixed,
    fixedShare,
    lunchShare: meals > 0 ? Math.round((lunches / meals) * 100) : 0,
    dinnerShare: meals > 0 ? Math.round((dinners / meals) * 100) : 0
  };
}

/**
 * 折线图下方摘要数据（保留作为"近 7 天参考"）
 */
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
  const lunchShare = lunchTotal + dinnerTotal === 0 ? 0 : Math.round((lunchTotal / (lunchTotal + dinnerTotal)) * 100);

  return {
    peakValue: peak.total,
    peakLabel: peak.label,
    averageValue: totalAverage,
    lunchShare,
    rangeText: totals.length ? `${Math.min(...totals)}-${Math.max(...totals)}` : "0-0",
    sum: totalSum
  };
}
