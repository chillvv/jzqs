// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DispatchProvider } from "./DispatchContext";
import { DispatchAreasPage } from "./DispatchAreasPage";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("@hello-pangea/dnd", () => ({
  DragDropContext: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  Droppable: ({ children }: { children: (provided: { innerRef: () => void; droppableProps: Record<string, never>; placeholder: null }) => React.ReactNode }) =>
    children({ innerRef: () => undefined, droppableProps: {}, placeholder: null }),
  Draggable: ({ children }: { children: (provided: { innerRef: () => void; draggableProps: Record<string, never>; dragHandleProps: Record<string, never> }, snapshot: { isDragging: boolean }) => React.ReactNode }) =>
    children({ innerRef: () => undefined, draggableProps: {}, dragHandleProps: {} }, { isDragging: false })
}));

vi.mock("../../shared/api/http", () => ({
  deleteDispatchArea: vi.fn(),
  deleteOrder: vi.fn(),
  fetchDispatchAreaBindings: vi.fn().mockResolvedValue([]),
  fetchDispatchManagedRiders: vi.fn().mockResolvedValue([]),
  moveOrderToArea: vi.fn(),
  renameDispatchArea: vi.fn(),
  assignRiderToArea: vi.fn(),
  assignRiderToAreaOrder: vi.fn(),
  reorderAreaOrders: vi.fn(),
  updateDispatchAreaBinding: vi.fn(),
  DispatchAreaDeleteBlockedError: class DispatchAreaDeleteBlockedError extends Error {}
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

describe("DispatchAreasPage", () => {
  it("renders dispatch area workspace", async () => {
    const view = renderIntoDom(
      <DispatchProvider>
        <DispatchAreasPage />
      </DispatchProvider>
    );

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(view.container.textContent).toContain("区域管理");
    expect(view.container.textContent).toContain("新增区域");
    expect(view.container.textContent).toContain("暂无区域，请先创建一个区域并绑定骑手。");

    view.unmount();
  });
});
