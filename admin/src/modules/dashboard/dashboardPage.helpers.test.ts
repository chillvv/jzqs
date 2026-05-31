import { describe, expect, it } from "vitest";
import { buildDashboardExceptionItems, buildDashboardHeroMetrics } from "./dashboardPage.helpers";
import type { DashboardOverviewResponse } from "../../shared/api/types";

const overview: DashboardOverviewResponse = {
  deliveredToday: 128,
  tomorrowMealCount: 96,
  tomorrowLunchCount: 52,
  tomorrowDinnerCount: 44,
  newCardsToday: 7,
  rechargeCustomersToday: 12,
  aftersaleToday: 3,
  cancellationsToday: 2,
  totalOrdersToday: 130,
  pendingOrdersToday: 5,
  pendingDispatchToday: 8,
  dispatchingOrdersToday: 11,
  deliveredOrdersToday: 104,
  lowBalanceCustomers: 9,
  openAftersaleCount: 4,
  specialOrdersToday: 6,
  menuRiskDays: 1,
  orderTrend: [],
  growthTrend: []
};

describe("buildDashboardHeroMetrics", () => {
  it("uses concise operation-focused detail copy", () => {
    expect(buildDashboardHeroMetrics(overview)).toEqual([
      { label: "今日送达", value: 128, unit: "份", tone: "blue", detail: "按送达回执" },
      { label: "明日订单", value: 96, unit: "份", tone: "cyan", detail: "午餐 52 / 晚餐 44" },
      { label: "今日新开卡", value: 7, unit: "人", tone: "emerald", detail: "首开与建档" },
      { label: "今日续卡充值", value: 12, unit: "人", tone: "violet", detail: "续卡与补餐" },
      { label: "今日售后", value: 3, unit: "单", tone: "amber", detail: "新增与处理中" },
      { label: "今日取消", value: 2, unit: "单", tone: "red", detail: "当日取消单" }
    ]);
  });
});

describe("buildDashboardExceptionItems", () => {
  it("keeps exception panel copy short and action-oriented", () => {
    expect(buildDashboardExceptionItems(overview)).toEqual([
      { label: "低余额客户", value: 9, tone: "amber", detail: "余额小于等于 3 餐" },
      { label: "待处理售后", value: 4, tone: "red", detail: "待闭环售后单" },
      { label: "特殊备注订单", value: 6, tone: "violet", detail: "含备注需跟进" },
      { label: "菜单配置风险", value: 1, tone: "blue", detail: "未来 7 天未配餐槽" }
    ]);
  });
});
