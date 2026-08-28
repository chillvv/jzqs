// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CustomerAssetPage } from "./CustomerAssetPage";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../../shared/api/http", () => ({
  swrFetcher: vi.fn().mockResolvedValue({ data: { items: [], page: 1, pageSize: 20, total: 0 } }),
  fetchCustomerAssets: vi.fn().mockResolvedValue({ items: [], page: 1, pageSize: 20, total: 0 }),
  fetchCustomerDetail: vi.fn().mockResolvedValue(null),
  fetchWalletTransactions: vi.fn().mockResolvedValue({ items: [], page: 1, pageSize: 20, total: 0 }),
  createCustomerProfile: vi.fn(),
  updateCustomerProfile: vi.fn(),
  createCustomerAddress: vi.fn(),
  updateCustomerAddress: vi.fn(),
  deleteCustomerAddress: vi.fn(),
  grantWalletMeals: vi.fn(),
  deductWalletMeals: vi.fn()
}));

vi.mock("../../shared/components/Toast", () => ({
  toast: vi.fn()
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

describe("CustomerAssetPage", () => {
  it("renders customer workspace and create action", async () => {
    const view = renderIntoDom(<CustomerAssetPage />);

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(view.container.textContent).toContain("客户经营中心");
    expect(view.container.textContent).toContain("新建客户档案");
    expect(view.container.textContent).toContain("暂无符合条件的客户记录");

    view.unmount();
  });
});
