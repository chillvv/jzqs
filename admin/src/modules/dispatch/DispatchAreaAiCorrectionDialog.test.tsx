// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DispatchAreaAiCorrectionDialog } from "./components/DispatchAreaAiCorrectionDialog";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const { confirmAreaAiCorrection, previewAreaAiCorrection } = vi.hoisted(() => ({
  previewAreaAiCorrection: vi.fn(),
  confirmAreaAiCorrection: vi.fn()
}));

vi.mock("../../shared/api/http", () => ({
  previewAreaAiCorrection,
  confirmAreaAiCorrection
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

function clickByText(container: HTMLElement, text: string) {
  const element = Array.from(container.querySelectorAll("button"))
    .find((node) => node.textContent?.includes(text));
  if (!element) {
    throw new Error(`未找到按钮: ${text}`);
  }
  act(() => {
    element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

function setTextareaValue(container: HTMLElement, labelText: string, value: string) {
  const label = Array.from(container.querySelectorAll("label"))
    .find((node) => node.textContent?.includes(labelText));
  const textarea = label?.querySelector("textarea");
  if (!textarea) {
    throw new Error(`未找到字段: ${labelText}`);
  }
  act(() => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, "value")?.set;
    setter?.call(textarea, value);
    textarea.dispatchEvent(new Event("input", { bubbles: true }));
    textarea.dispatchEvent(new Event("change", { bubbles: true }));
  });
}

const baseOrders = [
  { orderId: 1001, sequenceNumber: 1, customerName: "张三", deliveryAddress: "软件园A座", deliveryStatus: "PENDING", riderName: null, userNote: "", merchantRemark: "", referenceImageUrl: "", receiptUrl: "", receiptNote: "", deliveredAt: null, quantity: 1 },
  { orderId: 1002, sequenceNumber: 2, customerName: "李四", deliveryAddress: "软件园B座", deliveryStatus: "PENDING", riderName: null, userNote: "", merchantRemark: "", referenceImageUrl: "", receiptUrl: "", receiptNote: "", deliveredAt: null, quantity: 1 }
];

beforeEach(() => {
  previewAreaAiCorrection.mockReset();
  confirmAreaAiCorrection.mockReset();
  previewAreaAiCorrection.mockResolvedValue({
    correctionId: 7,
    aiInterpretationSummary: "AI 已理解午餐先写字楼再收住宅",
    replanStatus: "SUCCESS",
    replanError: "",
    finalOrderIds: [1002, 1001],
    memoryCandidates: [
      { memoryType: "ROUTE_PREFERENCE", title: "午餐先写字楼", summary: "A 区午餐高峰先写字楼后住宅" }
    ]
  });
});

afterEach(() => {
  document.body.innerHTML = "";
  vi.clearAllMocks();
});

describe("DispatchAreaAiCorrectionDialog", () => {
  it("submits chat correction and shows replanned result", async () => {
    const onPreviewApplied = vi.fn();
    const view = renderIntoDom(
      <DispatchAreaAiCorrectionDialog
        open
        areaCode="A01"
        originalOrders={baseOrders}
        onPreviewApplied={onPreviewApplied}
        onClose={() => undefined}
        onConfirmed={() => undefined}
      />
    );

    setTextareaValue(view.container, "纠正说明", "午餐先送写字楼");
    clickByText(view.container, "按我的意思重排");

    await act(async () => {
      await Promise.resolve();
    });

    expect(previewAreaAiCorrection).toHaveBeenCalledWith("A01", expect.objectContaining({
      originalOrderIds: [1001, 1002],
      merchantInstruction: "午餐先送写字楼"
    }));
    expect(onPreviewApplied).toHaveBeenCalledWith([1002, 1001]);
    expect(view.container.textContent).toContain("AI 已理解午餐先写字楼再收住宅");
    expect(view.container.textContent).toContain("#1002");
    view.unmount();
  });

  it("uses dragged order as merchant order ids during mixed correction", async () => {
    const view = renderIntoDom(
      <DispatchAreaAiCorrectionDialog
        open
        areaCode="A01"
        originalOrders={baseOrders}
        draftOrders={[baseOrders[1], baseOrders[0]]}
        onClose={() => undefined}
        onConfirmed={() => undefined}
      />
    );

    clickByText(view.container, "按我的意思重排");

    await act(async () => {
      await Promise.resolve();
    });

    expect(previewAreaAiCorrection).toHaveBeenCalledWith("A01", expect.objectContaining({
      originalOrderIds: [1001, 1002],
      merchantOrderIds: [1002, 1001]
    }));
    expect(view.container.textContent).toContain("已带入拖拽顺序");
    view.unmount();
  });

  it("rolls back to initial draft order after preview", async () => {
    const onRollbackPreview = vi.fn();
    const view = renderIntoDom(
      <DispatchAreaAiCorrectionDialog
        open
        areaCode="A01"
        originalOrders={baseOrders}
        draftOrders={[baseOrders[1], baseOrders[0]]}
        onRollbackPreview={onRollbackPreview}
        onClose={() => undefined}
        onConfirmed={() => undefined}
      />
    );

    clickByText(view.container, "按我的意思重排");

    await act(async () => {
      await Promise.resolve();
    });

    clickByText(view.container, "回退预览");

    expect(onRollbackPreview).toHaveBeenCalledWith([1002, 1001]);
    expect(view.container.textContent).not.toContain("AI 已理解午餐先写字楼再收住宅");
    view.unmount();
  });
});
