import { describe, expect, it } from "vitest";
import {
  buildMaintenanceLogRows,
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
    expect(resolveMaintenanceTriggerLabel("WECHAT_CLOUDFUNCTION")).toBe("云函数");
  });
});

describe("buildMaintenanceLogRows", () => {
  it("maps log items to table-friendly fields", () => {
    const rows = buildMaintenanceLogRows([
      {
        id: 8,
        jobType: "MANUAL_DATA_CLEANUP",
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
        errorDetail: null
      }
    ]);
    expect(rows[0]).toMatchObject({
      jobLabel: "手动数据清理",
      statusLabel: "成功",
      summary: "扫描 10 / 清理 10 / 失败 0"
    });
  });
});
