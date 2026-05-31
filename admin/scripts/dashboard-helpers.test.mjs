import assert from "node:assert/strict";
import {
  buildDashboardHeroMetrics,
  buildDashboardProgressItems,
  buildDashboardExceptionItems,
  buildDashboardOrderTrendSummary,
  normalizeDashboardOverview
} from "../temp-test/modules/dashboard/dashboardPage.helpers.js";

const data = {
  deliveredToday: 98,
  tomorrowMealCount: 76,
  tomorrowLunchCount: 43,
  tomorrowDinnerCount: 33,
  newCardsToday: 6,
  rechargeCustomersToday: 13,
  aftersaleToday: 4,
  cancellationsToday: 3,
  totalOrdersToday: 118,
  pendingOrdersToday: 7,
  pendingDispatchToday: 12,
  dispatchingOrdersToday: 8,
  deliveredOrdersToday: 98,
  lowBalanceCustomers: 19,
  openAftersaleCount: 4,
  specialOrdersToday: 11,
  menuRiskDays: 2,
  orderTrend: [
    { label: "05/09", total: 96, lunch: 54, dinner: 42 },
    { label: "05/10", total: 101, lunch: 58, dinner: 43 },
    { label: "05/11", total: 132, lunch: 76, dinner: 56 },
    { label: "05/12", total: 108, lunch: 61, dinner: 47 }
  ],
  growthTrend: [
    { label: "05/11", newCards: 4, recharges: 9 },
    { label: "05/12", newCards: 6, recharges: 13 }
  ]
};

{
  const metrics = buildDashboardHeroMetrics(data);
  assert.equal(metrics.length, 6);
  assert.deepEqual(metrics[0], {
    label: "今日送达总量",
    value: 98,
    unit: "份",
    tone: "blue",
    detail: "按今日送达回执统计"
  });
  assert.deepEqual(metrics[1], {
    label: "明日已下单份数",
    value: 76,
    unit: "份",
    tone: "cyan",
    detail: "午餐 43 / 晚餐 33"
  });
}

{
  const progress = buildDashboardProgressItems(data);
  assert.deepEqual(progress, [
    { label: "今日总订单", value: 118, tone: "blue", detail: "今天全部进入流程的订单" },
    { label: "待处理", value: 7, tone: "amber", detail: "待人工确认或继续流转" },
    { label: "待派单", value: 12, tone: "violet", detail: "已确认但未指派骑手" },
    { label: "配送中", value: 8, tone: "blue", detail: "骑手已接单，正在履约" },
    { label: "已送达", value: 98, tone: "emerald", detail: "当前已闭环送达" }
  ]);
}

{
  const exceptions = buildDashboardExceptionItems(data);
  assert.deepEqual(exceptions, [
    { label: "低余额客户", value: 19, tone: "amber", detail: "余额小于等于 3 餐" },
    { label: "待处理售后", value: 4, tone: "red", detail: "今日新增与未闭环售后" },
    { label: "特殊备注订单", value: 11, tone: "violet", detail: "重点客户、贴签、老板备注" },
    { label: "菜单配置风险", value: 2, tone: "blue", detail: "未来 7 天仍有未配置餐槽" }
  ]);
}

{
  const summary = buildDashboardOrderTrendSummary(data);
  assert.deepEqual(summary, {
    peakValue: 132,
    peakLabel: "05/11",
    averageValue: 109,
    lunchShare: 57,
    rangeText: "96-132"
  });
}

{
  const normalized = normalizeDashboardOverview({
    deliveredToday: 7,
    tomorrowMealCount: 9
  });
  assert.equal(normalized.deliveredToday, 7);
  assert.equal(normalized.tomorrowMealCount, 9);
  assert.equal(normalized.tomorrowLunchCount, 0);
  assert.deepEqual(normalized.orderTrend, []);
  assert.deepEqual(normalized.growthTrend, []);
}

console.log("dashboard helpers test: ok");
