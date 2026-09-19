// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DispatchProvider } from "./DispatchContext";
import { DispatchNotifyGapPage } from "./DispatchNotifyGapPage";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../../shared/api/http", () => ({
  fetchDeliveryNotifyGaps: vi.fn().mockResolvedValue([
    {
      orderId: 501,
      serveDate: "2026-09-19",
      mealPeriod: "LUNCH",
      quantity: 1,
      customerName: "午餐未通知客户",
      customerPhone: "13800000001",
      deliveryAddress: "软件园 A 座",
      deliveredAt: "2026-09-19T11:20:00",
      orderSource: "MINIAPP",
      fixedSubscription: false,
      reason: "NO_SUBSCRIPTION"
    },
    {
      orderId: 502,
      serveDate: "2026-09-19",
      mealPeriod: "DINNER",
      quantity: 2,
      customerName: "晚餐未通知客户",
      customerPhone: "13800000002",
      deliveryAddress: "软件园 B 座",
      deliveredAt: "2026-09-19T17:40:00",
      orderSource: "SUBSCRIPTION",
      fixedSubscription: true,
      reason: "REVOKED"
    }
  ]),
  extractAdminApiErrorMessage: vi.fn((error: unknown, fallback: string) => {
    if (error instanceof Error) {
      return error.message;
    }
    return fallback;
  })
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
  window.localStorage.clear();
});

describe("DispatchNotifyGapPage", () => {
  it("按餐段区分展示未收到提醒的客户，并提示另一餐段还有多少人", async () => {
    const view = renderIntoDom(
      <DispatchProvider>
        <DispatchNotifyGapPage />
      </DispatchProvider>
    );

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    const text = view.container.textContent || "";
    // 默认餐段为午餐：只展示午餐的未通知客户，并给出未送达原因
    expect(text).toContain("午餐未通知客户");
    expect(text).toContain("未授权取餐提醒");
    expect(text).not.toContain("晚餐未通知客户");
    // 来源口径与订单中心一致（小程序 / 后台录入 / 固定订餐）
    expect(text).toContain("小程序");
    expect(text).not.toContain("固定订餐");
    // 明确提示另一餐段还有多少人，避免运营只看当前餐段漏掉另一半
    expect(text).toContain("另一餐段还有 1 人");

    view.unmount();
  });
});
