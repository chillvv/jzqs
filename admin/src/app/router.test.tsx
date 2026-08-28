// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { matchRoutes, MemoryRouter, Navigate, Outlet, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { appRoutes, SettingsSectionRoute } from "./router";
import {
  buildSettingsSectionPath,
  DEFAULT_SETTINGS_SECTION,
  SETTINGS_SECTION
} from "../modules/settings/settingsSections";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./layout/AdminLayout", () => ({
  AdminLayout() {
    return (
      <div data-testid="admin-layout">
        <Outlet />
      </div>
    );
  }
}));

vi.mock("../modules/settings/SystemSettingsSectionPage", () => ({
  SystemSettingsSectionPage() {
    return <div>系统设置页面</div>;
  }
}));

function renderSectionRoute(initialPath: string) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);

  act(() => {
    root.render(
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/settings/:section" element={<SettingsSectionRoute />} />
          <Route path={buildSettingsSectionPath(DEFAULT_SETTINGS_SECTION)} element={<div>默认设置分组</div>} />
        </Routes>
      </MemoryRouter>
    );
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

describe("appRoutes", () => {
  it("将 /settings 配置为重定向到默认 section", () => {
    const matches = matchRoutes(appRoutes, "/settings");
    const leafRoute = matches?.[matches.length - 1]?.route;

    expect(leafRoute?.path).toBe("settings");
    expect(React.isValidElement(leafRoute?.element)).toBe(true);
    expect((leafRoute?.element as React.ReactElement).type).toBe(Navigate);
    expect((leafRoute?.element as React.ReactElement).props.to).toBe(buildSettingsSectionPath(DEFAULT_SETTINGS_SECTION));
  });

  it("将旧的 /maintenance 配置为重定向到新的 maintenance section", () => {
    const matches = matchRoutes(appRoutes, "/maintenance");
    const leafRoute = matches?.[matches.length - 1]?.route;

    expect(leafRoute?.path).toBe("maintenance");
    expect(React.isValidElement(leafRoute?.element)).toBe(true);
    expect((leafRoute?.element as React.ReactElement).type).toBe(Navigate);
    expect((leafRoute?.element as React.ReactElement).props.to).toBe(buildSettingsSectionPath(SETTINGS_SECTION.MAINTENANCE));
  });

  it("允许直接命中合法的 settings section 路由", () => {
    const matches = matchRoutes(appRoutes, buildSettingsSectionPath(SETTINGS_SECTION.MAINTENANCE));
    const leafRoute = matches?.[matches.length - 1]?.route;

    expect(leafRoute?.path).toBe("settings/:section");
  });

  it("允许命中新增加的 AI 智能调度 section 路由", () => {
    const matches = matchRoutes(appRoutes, buildSettingsSectionPath(SETTINGS_SECTION.AI_DISPATCH));
    const leafRoute = matches?.[matches.length - 1]?.route;

    expect(leafRoute?.path).toBe("settings/:section");
  });

  it("将非法 section 回退到默认 section", () => {
    const view = renderSectionRoute("/settings/unknown");

    expect(view.container.textContent).toContain("默认设置分组");
    view.unmount();
  });
});
