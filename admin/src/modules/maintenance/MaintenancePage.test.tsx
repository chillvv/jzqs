// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MaintenancePage } from "./MaintenancePage";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./MaintenanceSectionContent", () => ({
  MaintenanceSectionContent() {
    return <div data-testid="maintenance-section-content">复用维护内容</div>;
  }
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
  vi.clearAllMocks();
});

describe("MaintenancePage", () => {
  it("reuses MaintenanceSectionContent", () => {
    const view = renderIntoDom(<MaintenancePage />);

    expect(view.container.textContent).toContain("复用维护内容");
    view.unmount();
  });
});
