// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ConfirmActionDialog } from "./ConfirmActionDialog";
import { EmptyStateCard } from "./EmptyStateCard";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

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

describe("ConfirmActionDialog", () => {
  it("shows irreversible action copy for batch consume flow", () => {
    const view = renderIntoDom(
      <ConfirmActionDialog
        open
        title="确认批量核销"
        description="确定要给这 8 位客户扣餐吗？扣了就不能改了哦"
        confirmText="确定扣餐"
        cancelText="取消"
        onConfirm={() => undefined}
        onCancel={() => undefined}
      />
    );

    expect(view.container.textContent).toContain("扣了就不能改了哦");
    expect(view.container.textContent).toContain("确定扣餐");
    view.unmount();
  });
});

describe("EmptyStateCard", () => {
  it("renders action-oriented empty guidance", () => {
    const onPrimaryAction = vi.fn();
    const view = renderIntoDom(
      <EmptyStateCard
        title="还没有骑手哦"
        description="点右边的新增骑手按钮添加你的第一个骑手吧"
        primaryActionText="去添加"
        onPrimaryAction={onPrimaryAction}
      />
    );

    const button = view.container.querySelector("button");
    expect(button?.textContent).toBe("去添加");
    act(() => {
      button?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(onPrimaryAction).toHaveBeenCalledTimes(1);
    view.unmount();
  });
});
