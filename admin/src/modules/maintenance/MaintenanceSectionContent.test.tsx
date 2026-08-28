// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { SWRConfig } from "swr";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MaintenanceSectionContent } from "./MaintenanceSectionContent";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const { fetchMaintenanceLogs, fetchMaintenanceOverview, swrFetcher } = vi.hoisted(() => ({
  fetchMaintenanceOverview: vi.fn(),
  fetchMaintenanceLogs: vi.fn(),
  swrFetcher: vi.fn()
}));

vi.mock("../../shared/api/http", () => ({
  fetchMaintenanceOverview,
  fetchMaintenanceLogs,
  swrFetcher,
  triggerDataCleanup: vi.fn(),
  triggerMaintenanceModuleCleanup: vi.fn(),
  updateMaintenanceCleanupSettings: vi.fn()
}));

vi.mock("../../shared/components/Toast", () => ({
  toast: vi.fn()
}));

function renderIntoDom(element: React.ReactElement) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <SWRConfig value={{ provider: () => new Map() }}>
        {element}
      </SWRConfig>
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

beforeEach(() => {
  swrFetcher.mockImplementation(async (url: string) => {
    if (url === "/api/admin/maintenance/logs") {
      return { data: await fetchMaintenanceLogs() };
    }
    return { data: await fetchMaintenanceOverview() };
  });
  fetchMaintenanceOverview.mockResolvedValue({
    latestManual: {
      id: 1,
      jobType: "MANUAL_DATA_CLEANUP",
      moduleKey: null,
      triggerSource: "ADMIN",
      status: "SUCCESS",
      timeRangeLabel: "手动清理",
      startedAt: "2026-06-30 08:00:00",
      finishedAt: "2026-06-30 08:05:00",
      durationMs: 300000,
      scannedCount: 10,
      deletedCount: 10,
      failedCount: 0,
      message: "较早的手动清理",
      errorDetail: null,
      moduleSummaries: []
    },
    latestAuto: {
      id: 2,
      jobType: "AUTO_DATA_CLEANUP",
      moduleKey: null,
      triggerSource: "SYSTEM",
      status: "SUCCESS",
      timeRangeLabel: "自动清理",
      startedAt: "2026-06-30 12:00:00",
      finishedAt: "2026-06-30 12:03:00",
      durationMs: 180000,
      scannedCount: 20,
      deletedCount: 20,
      failedCount: 0,
      message: "最新的自动清理",
      errorDetail: null,
      moduleSummaries: []
    },
    latestCloudReceipt: null,
    latestCloudStorage: null,
    cleanupRules: [],
    nextAutoRunLabel: "每日 03:00"
  });
  fetchMaintenanceLogs.mockResolvedValue([]);
});

afterEach(() => {
  document.body.innerHTML = "";
  vi.clearAllMocks();
});

describe("MaintenanceSectionContent", () => {
  it("shows the newest successful execution by timestamp instead of fixed priority", async () => {
    const view = renderIntoDom(<MaintenanceSectionContent embedded />);

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(view.container.textContent).toContain("最新的自动清理");
    expect(view.container.textContent).not.toContain("较早的手动清理");
    view.unmount();
  });

  it("opens fine-grained log detail when a row is clicked", async () => {
    fetchMaintenanceLogs.mockResolvedValue([
      {
        id: 100,
        jobType: "MANUAL_DATA_CLEANUP",
        moduleKey: null,
        triggerSource: "ADMIN",
        status: "SUCCESS",
        timeRangeLabel: "订单<2026-07-06；回执<2026-08-04 00:00:00",
        startedAt: [2026, 8, 5, 3, 0, 0],
        finishedAt: [2026, 8, 5, 3, 2, 0],
        durationMs: 120000,
        scannedCount: 8,
        deletedCount: 8,
        failedCount: 0,
        message: "已完成板块检查",
        errorDetail: null,
        moduleSummaries: [
          {
            moduleKey: "ORDER_HISTORY",
            moduleLabel: "订单历史",
            scannedCount: 8,
            deletedCount: 8,
            failedCount: 0,
            timeRangeLabel: "订单<2026-07-06",
            summary: "扫描 8 条订单历史，清理 8 条",
            details: [
              {
                scopeLabel: "订单明细",
                tableName: "meal_slot_orders",
                scannedCount: 5,
                deletedCount: 5,
                failedCount: 0,
                rangeLabel: "订单<2026-07-06",
                note: "按订单状态和配送日期清理的历史订单明细"
              }
            ]
          }
        ]
      }
    ]);

    const view = renderIntoDom(<MaintenanceSectionContent embedded />);

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    const row = view.container.querySelector(".maintenance-log-table tbody tr");
    expect(row).not.toBeNull();

    act(() => {
      (row as HTMLElement).click();
    });

    expect(view.container.textContent).toContain("维护执行日志 #100");
    expect(view.container.textContent).toContain("2026-08-05 03:00");
    expect(view.container.textContent).toContain("2026-08-05 03:02");
    expect(view.container.textContent).toContain("订单明细");
    expect(view.container.textContent).toContain("meal_slot_orders");
    expect(view.container.textContent).toContain("按订单状态和配送日期清理的历史订单明细");
    view.unmount();
  });
});
