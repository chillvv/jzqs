// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DashboardPage } from "./DashboardPage";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AdminLayout } from "../../app/layout/AdminLayout";

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
});
