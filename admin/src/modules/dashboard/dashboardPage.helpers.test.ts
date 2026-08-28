import { describe, expect, it } from "vitest";
import { buildDashboardFlowSteps } from "./dashboardPage.helpers";
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
