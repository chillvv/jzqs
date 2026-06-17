import type { MaintenanceLogItemResponse, MaintenanceOverviewResponse } from "../../shared/api/types";
import { formatDateTimeLabel } from "../../shared/utils/dateTime";

export type MaintenanceTone = "green" | "orange" | "red" | "gray";
export type MaintenanceLogRow = {
  id: number;
  jobLabel: string;
  triggerLabel: string;
  statusLabel: string;
  tone: MaintenanceTone;
  timeLabel: string;
  rangeLabel: string;
  summary: string;
  message: string;
  errorDetail: string | null;
};

export function resolveMaintenanceStatusTone(status: string): MaintenanceTone {
  switch (status) {
    case "SUCCESS":
      return "green";
    case "PARTIAL_SUCCESS":
      return "orange";
    case "FAILED":
      return "red";
    default:
      return "gray";
  }
}

export function resolveMaintenanceStatusLabel(status: string) {
  switch (status) {
    case "SUCCESS":
      return "成功";
    case "PARTIAL_SUCCESS":
      return "部分成功";
    case "FAILED":
      return "失败";
    case "RUNNING":
      return "执行中";
    default:
      return "暂无记录";
  }
}

export function resolveMaintenanceJobLabel(jobType: string) {
  switch (jobType) {
    case "MANUAL_DATA_CLEANUP":
      return "手动数据清理";
    case "AUTO_DATA_CLEANUP":
      return "自动数据清理";
    case "ORDER_HISTORY_CLEANUP":
      return "订单历史";
    case "DISPATCH_BATCH_CLEANUP":
      return "配送批次";
    case "RECEIPT_RECORD_CLEANUP":
      return "回执记录";
    case "DISPATCH_REASSIGNMENT_CLEANUP":
      return "区域调整记录";
    case "ADDRESS_BINDING_CLEANUP":
      return "地址绑定";
    case "WALLET_TRANSACTION_CLEANUP":
      return "钱包流水";
    case "CLOUD_RECEIPT_CLEANUP":
      return "回执图片云清理";
    case "CLOUD_STORAGE_SWEEP":
      return "云存储扫描清理";
    default:
      return jobType || "维护任务";
  }
}

export function resolveMaintenanceTriggerLabel(triggerSource: string) {
  switch (triggerSource) {
    case "ADMIN":
      return "后台手动";
    case "SCHEDULED":
      return "系统定时";
    case "WECHAT_CLOUDFUNCTION":
      return "云函数";
    default:
      return triggerSource || "-";
  }
}

export function buildMaintenanceResultSummary(item: MaintenanceLogItemResponse | null) {
  if (!item) {
    return "还没有执行记录";
  }
  if (item.moduleSummaries?.length) {
    return item.moduleSummaries.map((summary) => summary.summary).join("；");
  }
  return `扫描 ${item.scannedCount} / 清理 ${item.deletedCount} / 失败 ${item.failedCount}`;
}

export function normalizeMaintenanceOverview(
  overview?: Partial<MaintenanceOverviewResponse> | null
): MaintenanceOverviewResponse {
  return {
    latestManual: overview?.latestManual ?? null,
    latestAuto: overview?.latestAuto ?? null,
    latestCloudReceipt: overview?.latestCloudReceipt ?? null,
    latestCloudStorage: overview?.latestCloudStorage ?? null,
    cleanupRules: Array.isArray(overview?.cleanupRules) ? overview.cleanupRules : [],
    nextAutoRunLabel: overview?.nextAutoRunLabel || "每日 03:00"
  };
}

export function buildMaintenanceLogRows(items: MaintenanceLogItemResponse[]): MaintenanceLogRow[] {
  return items.map((item) => ({
    id: item.id,
    jobLabel: resolveMaintenanceJobLabel(item.jobType),
    triggerLabel: resolveMaintenanceTriggerLabel(item.triggerSource),
    statusLabel: resolveMaintenanceStatusLabel(item.status),
    tone: resolveMaintenanceStatusTone(item.status),
    timeLabel: formatDateTimeLabel(item.finishedAt || item.startedAt),
    rangeLabel: item.timeRangeLabel || "-",
    summary: buildMaintenanceResultSummary(item),
    message: item.message || "-",
    errorDetail: item.errorDetail
  }));
}
