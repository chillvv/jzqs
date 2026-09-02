import { describe, expect, it } from "vitest";
import {
  buildDashboardFlowSteps,
  buildDashboardOrderTrendSummary,
  normalizeDashboardOverview
} from "./dashboardPage.helpers";
import type { DashboardOverviewResponse } from "../../shared/api/types";

const overview: DashboardOverviewResponse = {
  deliveredToday: 128,
  todayServeMealCount: 130,
  todayServeLunchCount: 70,
  todayServeDinnerCount: 60,
  todayRechargedMeals: 12,
  tomorrowMealCount: 96,
  tomorrowLunchCount: 52,
  tomorrowDinnerCount: 44,
  tomorrowCustomerCount: 40,
  tomorrowFixedOrderCount: 8,
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
  expiringSoonCustomers: 5,
  openAftersaleCount: 4,
  menuRiskDays: 1,
  orderTrend: [],
  growthTrend: []
};

describe("buildDashboardFlowSteps", () => {
  it("builds the four flow steps in the dashboard pipeline order", () => {
    expect(buildDashboardFlowSteps(overview)).toEqual([
      { index: 2, tone: "neutral", label: "待处理", value: 5, detail: "待人工确认/校核", path: "/orders" },
      { index: 3, tone: "blue", label: "待派单", value: 8, detail: "已确认，待指派骑手", path: "/dispatch" },
      { index: 4, tone: "blue", label: "配送中", value: 11, detail: "骑手履约中", path: "/dispatch" },
      {
        index: 5,
        tone: "emerald",
        label: "今日已送达",
        value: 104,
        detail: "完成率 80%",
        completionRate: 80,
        path: "/orders"
      }
    ]);
  });

  it("reports zero completion rate when no meals have been served yet", () => {
    const empty = buildDashboardFlowSteps({ ...overview, todayServeMealCount: 0, deliveredOrdersToday: 0 });
    expect(empty[3].completionRate).toBeNull();
    expect(empty[3].detail).toBe("等待今日首单送达");
  });
});

describe("buildDashboardOrderTrendSummary", () => {
  const trendOverview: DashboardOverviewResponse = {
    ...overview,
    orderTrend: [
      { label: "05/09", total: 96, lunch: 54, dinner: 42 },
      { label: "05/10", total: 101, lunch: 58, dinner: 43 },
      { label: "05/11", total: 132, lunch: 76, dinner: 56 },
      { label: "05/12", total: 108, lunch: 61, dinner: 47 }
    ]
  };

  it("summarizes peak, average, lunch share and value range of the trend", () => {
    expect(buildDashboardOrderTrendSummary(trendOverview)).toEqual({
      peakValue: 132,
      peakLabel: "05/11",
      averageValue: 109,
      lunchShare: 57,
      rangeText: "96-132",
      sum: 437
    });
  });

  it("falls back to neutral values when the trend is empty", () => {
    expect(buildDashboardOrderTrendSummary(overview)).toEqual({
      peakValue: 0,
      peakLabel: "-",
      averageValue: 0,
      lunchShare: 0,
      rangeText: "0-0",
      sum: 0
    });
  });
});

describe("normalizeDashboardOverview", () => {
  it("fills missing numeric and trend fields with safe defaults", () => {
    const normalized = normalizeDashboardOverview({
      deliveredToday: 7,
      tomorrowMealCount: 9
    });

    expect(normalized).toMatchObject({
      deliveredToday: 7,
      tomorrowMealCount: 9,
      tomorrowLunchCount: 0,
      deliveredOrdersToday: 7,
      orderTrend: [],
      growthTrend: []
    });
  });
});
