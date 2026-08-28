import { describe, expect, it } from "vitest";
import {
  buildMaintenanceLogDetailSections,
  buildMaintenanceLogRows,
  formatMaintenanceDurationLabel,
  normalizeMaintenanceOverview,
  resolveMaintenanceJobLabel,
  resolveMaintenanceStatusTone,
  resolveMaintenanceTriggerLabel
} from "./maintenancePage.helpers";

describe("resolveMaintenanceStatusTone", () => {
  it("maps maintenance statuses to tag tones", () => {
    expect(resolveMaintenanceStatusTone("SUCCESS")).toBe("green");
    expect(resolveMaintenanceStatusTone("PARTIAL_SUCCESS")).toBe("orange");
    expect(resolveMaintenanceStatusTone("FAILED")).toBe("red");
    expect(resolveMaintenanceStatusTone("RUNNING")).toBe("gray");
  });
});

describe("maintenance label resolvers", () => {
  it("returns readable labels for job type and trigger source", () => {
    expect(resolveMaintenanceJobLabel("CLOUD_STORAGE_SWEEP")).toBe("云存储扫描清理");
    expect(resolveMaintenanceJobLabel("ORDER_HISTORY_CLEANUP")).toBe("订单历史");
    expect(resolveMaintenanceJobLabel("RECEIPT_RECORD_CLEANUP")).toBe("回执记录");
    expect(resolveMaintenanceTriggerLabel("WECHAT_CLOUDFUNCTION")).toBe("云函数");
  });
});

describe("buildMaintenanceLogRows", () => {
  it("maps log items to table-friendly fields", () => {
    const rows = buildMaintenanceLogRows([
      {
        id: 8,
        jobType: "MANUAL_DATA_CLEANUP",
        moduleKey: null,
        triggerSource: "ADMIN",
        status: "SUCCESS",
        timeRangeLabel: "清理数据",
        startedAt: "2026-05-27T03:00:00",
        finishedAt: "2026-05-27T03:02:00",
        durationMs: 120000,
        scannedCount: 10,
        deletedCount: 10,
        failedCount: 0,
        message: "完成",
        errorDetail: null,
        moduleSummaries: []
      }
    ]);
    expect(rows[0]).toMatchObject({
      jobLabel: "手动数据清理",
      statusLabel: "成功",
      summary: "扫描 10 / 清理 10 / 失败 0"
    });
  });

  it("keeps the summary format stable for cleanup-center cards", () => {
    const rows = buildMaintenanceLogRows([
      {
        id: 18,
        jobType: "ORDER_HISTORY_CLEANUP",
        moduleKey: null,
        triggerSource: "ADMIN",
        status: "PARTIAL_SUCCESS",
        timeRangeLabel: "订单<2026-05-01",
        startedAt: "2026-06-17T10:00:00",
        finishedAt: "2026-06-17T10:03:00",
        durationMs: 180000,
        scannedCount: 32,
        deletedCount: 8,
        failedCount: 1,
        message: "订单历史清理完成",
        errorDetail: null,
        moduleSummaries: []
      }
    ]);
    expect(rows[0]?.summary).toBe("扫描 32 / 清理 8 / 失败 1");
  });
});

describe("formatMaintenanceDurationLabel", () => {
  it("formats duration into readable labels", () => {
    expect(formatMaintenanceDurationLabel(null)).toBe("-");
    expect(formatMaintenanceDurationLabel(0)).toBe("-");
    expect(formatMaintenanceDurationLabel(500)).toBe("1 秒");
    expect(formatMaintenanceDurationLabel(120000)).toBe("2 分钟");
    expect(formatMaintenanceDurationLabel(125000)).toBe("2 分 5 秒");
  });
});

describe("buildMaintenanceLogDetailSections", () => {
  it("builds fine-grained per-module sections from module summaries", () => {
    const sections = buildMaintenanceLogDetailSections({
      id: 21,
      jobType: "MANUAL_DATA_CLEANUP",
      moduleKey: null,
      triggerSource: "ADMIN",
      status: "SUCCESS",
      timeRangeLabel: "订单<2026-07-06；回执<2026-08-04 00:00:00",
      startedAt: "2026-08-05T03:00:00",
      finishedAt: "2026-08-05T03:02:00",
      durationMs: 120000,
      scannedCount: 12,
      deletedCount: 12,
      failedCount: 0,
      message: "已完成 2 个板块检查",
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
            },
            {
              scopeLabel: "订单主记录",
              tableName: "daily_orders",
              scannedCount: 3,
              deletedCount: 3,
              failedCount: 0,
              rangeLabel: "订单<2026-07-06",
              note: "明细清理完成后无残留引用的历史订单主表"
            }
          ]
        }
      ]
    });

    expect(sections).toHaveLength(1);
    expect(sections[0]).toMatchObject({
      moduleLabel: "订单历史",
      deletedCount: 8
    });
    expect(sections[0]?.details).toHaveLength(2);
    expect(sections[0]?.details[0]).toMatchObject({
      tableName: "meal_slot_orders",
      deletedCount: 5
    });
  });

  it("defaults missing details to an empty list", () => {
    const sections = buildMaintenanceLogDetailSections({
      id: 22,
      jobType: "CLOUD_STORAGE_SWEEP",
      moduleKey: null,
      triggerSource: "WECHAT_CLOUDFUNCTION",
      status: "SUCCESS",
      timeRangeLabel: "云清理任务执行",
      startedAt: "2026-08-05T06:00:00",
      finishedAt: "2026-08-05T06:00:30",
      durationMs: 30000,
      scannedCount: 12,
      deletedCount: 12,
      failedCount: 0,
      message: "云存储扫描清理完成",
      errorDetail: null,
      moduleSummaries: []
    });

    expect(sections).toEqual([]);
  });
});

describe("normalizeMaintenanceOverview", () => {
  it("fills missing cleanup rules when backend still returns the old overview shape", () => {
    const normalized = normalizeMaintenanceOverview({
      latestManual: null,
      latestAuto: null,
      latestCloudReceipt: null,
      latestCloudStorage: null
    } as any);

    expect(normalized.cleanupRules).toEqual([]);
    expect(normalized.nextAutoRunLabel).toBe("每日 03:00");
  });
});
