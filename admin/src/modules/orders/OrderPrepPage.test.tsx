// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { OrderPrepPage } from "./OrderPrepPage";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("xlsx", () => ({}));

vi.mock("../../shared/api/http", () => ({
  swrFetcher: vi.fn((url: string) => {
    if (url.includes("/prep-stats")) {
      return Promise.resolve({
        data: {
          totalMeals: 0,
          lunchCount: 0,
          dinnerCount: 0,
          selfOrderCount: 0,
          staffOrderCount: 0,
          subscriptionCount: 0,
          adminRemarkCount: 0,
          labelRequiredCount: 0
        }
      });
    }
    if (url.includes("/subscription-confirmations")) {
      return Promise.resolve({ data: [] });
    }
    return Promise.resolve({ data: { items: [], page: 1, pageSize: 10, total: 0 } });
  }),
  assignDispatch: vi.fn(),
  deleteDeliveryReceipt: vi.fn(),
  deleteOrder: vi.fn(),
  cancelSubscriptionConfirmation: vi.fn(),
  confirmSubscription: vi.fn(),
  createManualOrder: vi.fn(),
  fetchDispatchAreaBindings: vi.fn().mockResolvedValue([]),
  fetchDispatchManagedRiders: vi.fn().mockResolvedValue([]),
  fetchCurrentMenuWeek: vi.fn().mockResolvedValue(null),
  fetchOrderPrepList: vi.fn().mockResolvedValue({ items: [], page: 1, pageSize: 10, total: 0 }),
  fetchOrderPrepStats: vi.fn().mockResolvedValue({
    totalMeals: 0,
    lunchCount: 0,
    dinnerCount: 0,
    selfOrderCount: 0,
    staffOrderCount: 0,
    subscriptionCount: 0,
    adminRemarkCount: 0,
    labelRequiredCount: 0
  }),
  fetchSubscriptionConfirmations: vi.fn().mockResolvedValue([]),
  recordDeliveryReceipt: vi.fn(),
  uploadDeliveryReceiptImage: vi.fn(),
  fetchSubscriptionPreview: vi.fn().mockResolvedValue([]),
  bulkImportSubscription: vi.fn(),
  fetchRemarkSuggestions: vi.fn().mockResolvedValue({ items: [] }),
  updateOrderProfile: vi.fn(),
  applyOrderSpecialDispatch: vi.fn(),
  clearOrderSpecialDispatch: vi.fn(),
  checkSubscriptionPreview: vi.fn(),
  createOrderAftersale: vi.fn(),
  directRefund: vi.fn(),
  searchManualCreateCustomers: vi.fn().mockResolvedValue([])
}));

vi.mock("../../shared/components/Toast", () => ({
  toast: vi.fn()
}));

vi.mock("./components/OrderPrepAssignModal", () => ({ OrderPrepAssignModal: () => null }));
vi.mock("./components/OrderPrepEditModal", () => ({ OrderPrepEditModal: () => null }));
vi.mock("./components/OrderPrepSpecialProcessModal", () => ({ OrderPrepSpecialProcessModal: () => null }));
vi.mock("./components/OrderPrepReceiptModal", () => ({ OrderPrepReceiptModal: () => null }));
vi.mock("./components/OrderPrepManualCreateModal", () => ({ OrderPrepManualCreateModal: () => null }));
vi.mock("./components/OrderPrepSubscriptionPreviewModal", () => ({ OrderPrepSubscriptionPreviewModal: () => null }));
vi.mock("./components/OrderPrepAftersaleModal", () => ({ OrderPrepAftersaleModal: () => null }));
vi.mock("./SubscriptionManagementTab", () => ({ SubscriptionManagementTab: () => <div>订阅管理占位</div> }));

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
  window.localStorage.clear();
});

describe("OrderPrepPage", () => {
  it("renders order operations workspace", async () => {
    const view = renderIntoDom(
      <MemoryRouter initialEntries={["/orders"]}>
        <OrderPrepPage />
      </MemoryRouter>
    );

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(view.container.textContent).toContain("订单运营中心");
    expect(view.container.textContent).toContain("录入代客订单");
    expect(view.container.textContent).toContain("普通订单");

    view.unmount();
  });
});
