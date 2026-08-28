// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DispatchProvider } from "./DispatchContext";
import { DispatchHomePage } from "./DispatchHomePage";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../../shared/api/http", () => ({
  batchAssignDispatchPendingOrders: vi.fn(),
  deleteOrder: vi.fn(),
  fetchDispatchOverview: vi.fn().mockResolvedValue({
    pendingCount: 3,
    dispatchingCount: 1,
    missingRiderAreaCount: 0
  }),
  fetchDispatchAreaBindings: vi.fn().mockRejectedValue(new Error("区域加载失败")),
  fetchDispatchPendingItems: vi.fn().mockResolvedValue([
    { orderId: 101, customerName: "张三", deliveryAddress: "软件园 A 座" }
  ]),
  extractAdminApiErrorMessage: vi.fn((error: unknown, fallback: string) => {
    if (error instanceof Error) {
      return error.message;
    }
    return fallback;
  })
}));

vi.mock("../../shared/realtime/adminRealtime", () => ({
  useAdminRealtime: vi.fn(() => () => undefined)
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

describe("DispatchHomePage", () => {
  it("shows partial refresh warning when some sections fail to load", async () => {
    const view = renderIntoDom(
      <DispatchProvider>
        <DispatchHomePage />
      </DispatchProvider>
    );

    expect(view.container.textContent).toContain("分单数据加载中...");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(view.container.textContent).toContain("部分分单数据刷新失败：区域");
    expect(view.container.textContent).toContain("张三");

    view.unmount();
  });
});
