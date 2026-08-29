// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DashboardPage } from "./DashboardPage";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AdminLayout } from "../../app/layout/AdminLayout";
import { fetchDashboardOverview } from "../../shared/api/http";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../../shared/api/http", () => ({
  fetchDashboardOverview: vi.fn().mockResolvedValue({
    deliveredToday: 0,
    tomorrowMealCount: 0,
    newCardsToday: 0,
    rechargeCustomersToday: 0,
    aftersaleToday: 0,
    cancellationsToday: 0,
    totalOrdersToday: 10,
    pendingOrdersToday: 5,
    pendingDispatchToday: 0,
    dispatchingOrdersToday: 0,
    deliveredOrdersToday: 0,
    lowBalanceCustomers: 0,
    expiringSoonCustomers: 0,
    openAftersaleCount: 0,
    menuRiskDays: 0,
    orderTrend: [],
    growthTrend: []
  }),
  fetchAdminProfile: vi.fn().mockResolvedValue({ displayName: "Admin", phone: "13800138000" })
}));

// jsdom 无 canvas，真实 ECharts 无法渲染；mock 组件并暴露收到的配置，便于断言图表参数
vi.mock("../../shared/components/EChart", () => ({
  EChart: (props: { option: unknown; height?: number | string; ariaLabel?: string }) => (
    <div
      data-testid="echart-mock"
      data-option={JSON.stringify(props.option)}
      data-height={String(props.height)}
      aria-label={props.ariaLabel}
    />
  )
}));

function renderIntoDom(element: React.ReactElement) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(element);
  });
  return {
    container,
    unmount() {
      act(() => {
        root.unmount();
      });
      container.remove();
    }
  };
}

afterEach(() => {
  document.body.innerHTML = "";
});

describe("DashboardPage", () => {
  it("shows current order flow panel", async () => {
    const view = renderIntoDom(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Routes>
          <Route path="/dashboard" element={<DashboardPage />} />
        </Routes>
      </MemoryRouter>
    );
    
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(view.container.textContent).toContain("经营看板");
    expect(view.container.textContent).toContain("待处理");
    
    view.unmount();
  });

  it("passes correct series data and value y-axis to trend chart", async () => {
    vi.mocked(fetchDashboardOverview).mockResolvedValue({
      todayServeMealCount: 0,
      todayServeLunchCount: 0,
      todayServeDinnerCount: 0,
      todayRechargedMeals: 0,
      tomorrowMealCount: 0,
      tomorrowLunchCount: 0,
      tomorrowDinnerCount: 0,
      tomorrowCustomerCount: 0,
      tomorrowFixedOrderCount: 0,
      deliveredToday: 0,
      totalOrdersToday: 10,
      pendingOrdersToday: 5,
      pendingDispatchToday: 0,
      dispatchingOrdersToday: 0,
      deliveredOrdersToday: 0,
      newCardsToday: 0,
      rechargeCustomersToday: 0,
      aftersaleToday: 0,
      cancellationsToday: 0,
      lowBalanceCustomers: 0,
      expiringSoonCustomers: 0,
      openAftersaleCount: 0,
      menuRiskDays: 0,
      orderTrend: [
        { label: "08/23", total: 10, lunch: 6, dinner: 4 },
        { label: "08/24", total: 40, lunch: 20, dinner: 20 },
        { label: "08/25", total: 25, lunch: 15, dinner: 10 },
        { label: "08/26", total: 60, lunch: 35, dinner: 25 },
        { label: "08/27", total: 30, lunch: 18, dinner: 12 },
        { label: "08/28", total: 50, lunch: 30, dinner: 20 },
        { label: "08/29", total: 70, lunch: 40, dinner: 30 }
      ],
      growthTrend: []
    });

    const view = renderIntoDom(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Routes>
          <Route path="/dashboard" element={<DashboardPage />} />
        </Routes>
      </MemoryRouter>
    );

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    const chartEl = view.container.querySelector('[data-testid="echart-mock"]');
    expect(chartEl).not.toBeNull();
    expect(chartEl?.getAttribute("aria-label")).toBe("近 7 天订单趋势图");

    const option = JSON.parse(chartEl?.getAttribute("data-option") || "{}");
    // Y 轴交给 ECharts 的 value 轴（值越大越靠上），杜绝手写坐标方向颠倒
    expect(option.yAxis.type).toBe("value");
    expect(option.yAxis.min).toBe(0);
    expect(option.xAxis.data).toEqual(["08/23", "08/24", "08/25", "08/26", "08/27", "08/28", "08/29"]);
    expect(option.series).toHaveLength(3);
    expect(option.series[0].name).toBe("全部");
    expect(option.series[0].data).toEqual([10, 40, 25, 60, 30, 50, 70]);
    expect(option.series[1].name).toBe("午餐");
    expect(option.series[1].data).toEqual([6, 20, 15, 35, 18, 30, 40]);
    expect(option.series[2].name).toBe("晚餐");
    // 高峰标注改用 ECharts markPoint（max），不依赖手算峰值坐标
    expect(option.series[0].markPoint.data[0].type).toBe("max");

    view.unmount();
  });
});
