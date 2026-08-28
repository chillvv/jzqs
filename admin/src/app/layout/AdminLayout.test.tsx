// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdminLayout } from "./AdminLayout";
import { ADMIN_AUTH_STORAGE_KEY } from "../../modules/auth/adminAuth.helpers";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const { fetchAdminProfile } = vi.hoisted(() => ({
  fetchAdminProfile: vi.fn()
}));

vi.mock("../../shared/api/http", () => ({
  fetchAdminProfile,
  logoutAdmin: vi.fn().mockResolvedValue(undefined),
  changeAdminPassword: vi.fn().mockResolvedValue(undefined)
}));

vi.mock("../../shared/hooks/useScale", () => ({
  useScale: vi.fn(() => undefined)
}));

vi.mock("../../shared/components/Toast", () => ({
  toast: vi.fn(),
  ToastContainer() {
    return null;
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

beforeEach(() => {
  window.localStorage.setItem(ADMIN_AUTH_STORAGE_KEY, JSON.stringify({
    token: "token",
    userId: 1,
    displayName: "管理员",
    phone: "13800000000",
    role: "ADMIN"
  }));
  fetchAdminProfile.mockResolvedValue({
    userId: 1,
    displayName: "管理员",
    phone: "13800000000",
    role: "ADMIN"
  });
});

afterEach(() => {
  document.body.innerHTML = "";
  window.localStorage.clear();
  vi.clearAllMocks();
});

describe("AdminLayout", () => {
  it("shows settings in sidebar without a standalone maintenance entry", async () => {
    const view = renderIntoDom(
      <MemoryRouter initialEntries={["/settings/maintenance"]}>
        <Routes>
          <Route path="/" element={<AdminLayout />}>
            <Route path="settings/:section" element={<div>设置内容</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    const navMenu = view.container.querySelector(".nav-menu");
    expect(navMenu?.textContent).toContain("系统设置");
    expect(navMenu?.textContent).not.toContain("数据清理与系统维护");

    const settingsLink = Array.from(view.container.querySelectorAll("a")).find((link) => link.textContent?.includes("系统设置"));
    expect(settingsLink?.getAttribute("href")).toBe("/settings/basic");

    view.unmount();
  });
});
