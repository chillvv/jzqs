// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AdminDialog } from "./AdminDialog";

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

describe("AdminDialog", () => {
  it("does not render dialog content when closed", () => {
    const view = renderIntoDom(
      <AdminDialog open={false} title="关闭态弹窗" onClose={vi.fn()}>
        <div>隐藏内容</div>
      </AdminDialog>
    );

    expect(view.container.textContent).toBe("");
    expect(view.container.querySelector(".admin-modal-overlay")).toBeNull();

    view.unmount();
  });

  it("renders dialog content when open", () => {
    const view = renderIntoDom(
      <AdminDialog open title="打开态弹窗" onClose={vi.fn()}>
        <div>可见内容</div>
      </AdminDialog>
    );

    expect(view.container.textContent).toContain("打开态弹窗");
    expect(view.container.textContent).toContain("可见内容");
    expect(view.container.querySelector(".admin-modal-overlay")).not.toBeNull();

    view.unmount();
  });
});
