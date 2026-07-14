import React, { useEffect, useMemo, useState } from "react";
import useSWR from "swr";
import { CheckCircle, Clock3, Database, ShieldCheck, SlidersHorizontal, Trash2 } from "lucide-react";
import {
  swrFetcher,
  triggerDataCleanup,
  triggerMaintenanceModuleCleanup,
  updateMaintenanceCleanupSettings
} from "../../shared/api/http";
import type {
  MaintenanceCleanupRuleResponse,
  MaintenanceLogItemResponse,
  MaintenanceOverviewResponse
} from "../../shared/api/types";
import {
  buildMaintenanceLogRows,
  normalizeMaintenanceOverview,
  resolveMaintenanceStatusLabel,
  type MaintenanceTone
} from "./maintenancePage.helpers";
import { formatDateTimeLabel } from "../../shared/utils/dateTime";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { AsyncContentView, type AsyncContentViewStatus } from "../../shared/components/AsyncContentView";
import { toast } from "../../shared/components/Toast";
import { SafeInput } from "../../shared/components/SafeInput";

const EMPTY_OVERVIEW: MaintenanceOverviewResponse = {
  latestManual: null,
  latestAuto: null,
  latestCloudReceipt: null,
  latestCloudStorage: null,
  cleanupRules: [],
  nextAutoRunLabel: "每日 03:00"
};

const BUSINESS_RULE_KEYS = ["ORDER_HISTORY", "RECEIPT_RECORD", "WALLET_TRANSACTION"] as const;
const BUSINESS_RULE_KEY_SET = new Set<string>(BUSINESS_RULE_KEYS);

function resolveBusinessRuleLabel(moduleKey: string) {
  switch (moduleKey) {
    case "ORDER_HISTORY":
      return "订单历史";
    case "RECEIPT_RECORD":
      return "回执记录";
    case "WALLET_TRANSACTION":
      return "钱包流水";
    default:
      return "维护规则";
  }
}

function resolveBusinessRuleDescription(moduleKey: string) {
  switch (moduleKey) {
    case "ORDER_HISTORY":
      return "保留顾客与商家近期需要查询的订单记录，超过保留周期后再清理。";
    case "RECEIPT_RECORD":
      return "保留短期配送签收与回执信息，适合更高频的自动清理。";
    case "WALLET_TRANSACTION":
      return "保留更长时间的餐包加减流水，方便后续核对。";
    default:
      return "按当前规则执行清理。";
  }
}

function resolveToneTagClass(tone: MaintenanceTone) {
  if (tone === "green") return "tag-green";
  if (tone === "orange") return "tag-orange";
  if (tone === "red") return "tag-red";
  return "tag-gray";
}

function areRulesEqual(
  left: MaintenanceCleanupRuleResponse[],
  right: MaintenanceCleanupRuleResponse[]
) {
  if (left.length !== right.length) {
    return false;
  }
  return left.every((item, index) => {
    const target = right[index];
    return Boolean(target)
      && item.moduleKey === target.moduleKey
      && item.retentionValue === target.retentionValue
      && item.retentionUnit === target.retentionUnit
      && item.autoEnabled === target.autoEnabled;
  });
}

function normalizeTimestamp(value?: string | null) {
  if (!value) {
    return 0;
  }
  const normalized = value.includes("T") ? value : value.replace(" ", "T");
  const timestamp = Date.parse(normalized);
  return Number.isNaN(timestamp) ? 0 : timestamp;
}

interface MaintenanceSectionContentProps {
  embedded?: boolean;
}

export function MaintenanceSectionContent({ embedded = false }: MaintenanceSectionContentProps) {
  const { data: overviewRes, error: overviewError, isLoading: overviewLoading, mutate: mutateOverview } = useSWR(
    '/api/admin/maintenance/overview',
    swrFetcher,
    { revalidateOnFocus: false }
  );

  const { data: logsRes, error: logsError, isLoading: logsLoading, mutate: mutateLogs } = useSWR(
    '/api/admin/maintenance/logs',
    swrFetcher,
    { revalidateOnFocus: false }
  );

  const overviewData = overviewRes?.data as MaintenanceOverviewResponse | undefined;
  const logsData = logsRes?.data as MaintenanceLogItemResponse[] | undefined;

  const overview = useMemo(() => {
    return overviewData ? normalizeMaintenanceOverview(overviewData) : EMPTY_OVERVIEW;
  }, [overviewData]);

  const logs = logsData || [];
  const loading = overviewLoading || logsLoading;
  const error = overviewError ? (overviewError?.response?.data?.message || overviewError?.message || String(overviewError)) : logsError ? (logsError?.response?.data?.message || logsError?.message || String(logsError)) : null;

  const [editableRules, setEditableRules] = useState<MaintenanceCleanupRuleResponse[]>([]);
  const [rulesInitialized, setRulesInitialized] = useState(false);
  const [cleaning, setCleaning] = useState(false);
  const [savingRules, setSavingRules] = useState(false);
  const [runningModuleKey, setRunningModuleKey] = useState<string | null>(null);
  const [result, setResult] = useState<{ status: string; message: string } | null>(null);
  const [isCleanupConfirmOpen, setIsCleanupConfirmOpen] = useState(false);
  const [isRuleDialogOpen, setIsRuleDialogOpen] = useState(false);

  useEffect(() => {
    if (overviewData && !rulesInitialized) {
      setEditableRules(normalizeMaintenanceOverview(overviewData).cleanupRules);
      setRulesInitialized(true);
    }
  }, [overviewData, rulesInitialized]);

  useEffect(() => {
    if (error) {
      toast(error, "error");
    }
  }, [error]);

  async function handleCleanup() {
    setCleaning(true);
    try {
      setIsCleanupConfirmOpen(false);
      const response = await triggerDataCleanup();
      setResult(response);
      await Promise.all([mutateOverview(), mutateLogs()]);
      toast(response.message || "数据清理已完成");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || String(err), "error");
    } finally {
      setCleaning(false);
    }
  }

  async function handleModuleCleanup(moduleKey: string) {
    setRunningModuleKey(moduleKey);
    try {
      const response = await triggerMaintenanceModuleCleanup(moduleKey);
      setResult(response);
      await Promise.all([mutateOverview(), mutateLogs()]);
      toast(response.message || "模块清理已执行");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || String(err), "error");
    } finally {
      setRunningModuleKey(null);
    }
  }

  async function handleSaveRules() {
    setSavingRules(true);
    try {
      const nextOverview = await updateMaintenanceCleanupSettings({
        rules: editableRules.map((rule) => ({
          moduleKey: rule.moduleKey,
          retentionValue: Math.max(1, Number(rule.retentionValue || 1)),
          retentionUnit: rule.retentionUnit || "DAY",
          autoEnabled: rule.autoEnabled !== false
        }))
      });
      const normalizedOverview = normalizeMaintenanceOverview(nextOverview);
      mutateOverview({ data: nextOverview, code: 200, message: "" }, { revalidate: false });
      setEditableRules(normalizedOverview.cleanupRules);
      setResult({ status: "SUCCESS", message: "清理规则已保存" });
      toast("清理规则已保存");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || String(err), "error");
    } finally {
      setSavingRules(false);
    }
  }

  function updateRule(moduleKey: string, patch: Partial<MaintenanceCleanupRuleResponse>) {
    setEditableRules((current) => current.map((rule) => (
      rule.moduleKey === moduleKey
        ? { ...rule, ...patch }
        : rule
    )));
  }

  const logRows = useMemo(() => buildMaintenanceLogRows(logs), [logs]);
  const maintenanceLogStatus: AsyncContentViewStatus = loading
    ? "loading"
    : error
      ? "error"
      : logRows.length === 0
        ? "empty"
        : "success";
  const hasUnsavedRuleChanges = useMemo(
    () => !areRulesEqual(editableRules, overview.cleanupRules || []),
    [editableRules, overview.cleanupRules]
  );
  const visibleRules = useMemo(
    () => editableRules.filter((rule) => BUSINESS_RULE_KEY_SET.has(rule.moduleKey)),
    [editableRules]
  );
  const latestSuccess = [overview.latestManual, overview.latestAuto, overview.latestCloudReceipt, overview.latestCloudStorage]
    .filter(Boolean)
    .filter((item) => item?.status === "SUCCESS" || item?.status === "PARTIAL_SUCCESS")
    .sort((left, right) => {
      const leftTime = normalizeTimestamp(left?.finishedAt || left?.startedAt);
      const rightTime = normalizeTimestamp(right?.finishedAt || right?.startedAt);
      return rightTime - leftTime;
    })[0] || null;

  return (
    <div className={embedded ? "maintenance-page" : "customer-asset-page maintenance-page"}>
      {!embedded ? (
        <div className="page-header">
          <div>
            <h2 className="page-title">系统维护</h2>
            <p className="page-description">按板块设置清理周期，支持手动执行，并保留每次执行日志。</p>
          </div>
          <div className="page-header__actions">
            <button
              className="btn btn-outline"
              disabled={loading}
              onClick={() => setIsRuleDialogOpen(true)}
            >
              <SlidersHorizontal size={16} />
              规则设置
            </button>
            <button className="btn btn-primary" disabled={cleaning || loading} onClick={() => setIsCleanupConfirmOpen(true)}>
              <Trash2 size={16} />
              {cleaning ? "执行中..." : "立即执行全部"}
            </button>
          </div>
        </div>
      ) : (
        <div className="page-header">
          <div>
            <h3 className="page-title">系统维护</h3>
            <p className="page-description">按板块设置清理周期，支持手动执行，并保留每次执行日志。</p>
          </div>
          <div className="page-header__actions">
            <button
              className="btn btn-outline"
              disabled={loading}
              onClick={() => setIsRuleDialogOpen(true)}
            >
              <SlidersHorizontal size={16} />
              规则设置
            </button>
            <button className="btn btn-primary" disabled={cleaning || loading} onClick={() => setIsCleanupConfirmOpen(true)}>
              <Trash2 size={16} />
              {cleaning ? "执行中..." : "立即执行全部"}
            </button>
          </div>
        </div>
      )}

      <section className="maintenance-rule-guide admin-panel">
        <div className="maintenance-rule-guide__summary">
          <div className="maintenance-rule-guide__title">保存规则是做什么的</div>
          <div className="maintenance-rule-guide__desc">
            规则已经收纳到“规则设置”里；保存后将用于后续自动清理和立即执行全部，如果刚改了保留时长或自动清理开关，需要先保存再按新规则执行。
          </div>
        </div>
        <div className={`maintenance-rule-guide__status ${hasUnsavedRuleChanges ? "is-pending" : "is-synced"}`}>
          <div className="maintenance-rule-guide__status-label">规则状态</div>
          <div className="maintenance-rule-guide__status-value">
            {hasUnsavedRuleChanges ? "当前有未保存修改" : "规则已同步到服务器"}
          </div>
          <div className="maintenance-rule-guide__status-note">
            {hasUnsavedRuleChanges
              ? "点击“保存并应用规则”后，新的保留时长和自动清理开关才会正式生效。"
              : "现在页面上的规则就是服务器实际执行的规则。"}
          </div>
        </div>
      </section>

      <section className="maintenance-overview-strip admin-panel">
        <div className="maintenance-overview-strip__hero">
          <div className="maintenance-overview-strip__icon">
            <Database size={24} />
          </div>
          <div className="maintenance-overview-strip__copy">
            <div className="maintenance-overview-strip__title">清理中心总览</div>
            <div className="maintenance-overview-strip__note">
              当前维护状态
              <span className="maintenance-overview-strip__status">{loading ? "同步中" : "可执行"}</span>
            </div>
          </div>
        </div>

        <div className="maintenance-overview-metrics">
          <div className="maintenance-overview-metric">
            <div className="maintenance-overview-metric__label">下次自动清理时间</div>
            <div className="maintenance-overview-metric__value">
              <Clock3 size={14} />
              {overview.nextAutoRunLabel || "每日 03:00"}
            </div>
          </div>
          <div className="maintenance-overview-metric">
            <div className="maintenance-overview-metric__label">最近一次执行结果</div>
            <div className="maintenance-overview-metric__value">
              {latestSuccess ? (
                <>
                  <ShieldCheck size={14} />
                  {formatDateTimeLabel(latestSuccess.finishedAt || latestSuccess.startedAt)}
                </>
              ) : (
                "还没有成功记录"
              )}
            </div>
            <div className="maintenance-overview-metric__sub">
              {latestSuccess ? latestSuccess.message : "等待首次执行后生成摘要"}
            </div>
          </div>
          {result ? (
            <div className="maintenance-overview-metric maintenance-overview-metric--success">
              <div className="maintenance-overview-metric__label">最近操作</div>
              <div className="maintenance-overview-metric__value">
                <CheckCircle size={14} />
                {result.message}
              </div>
            </div>
          ) : null}
        </div>
      </section>

      <section className="table-container maintenance-log-table">
        <div className="maintenance-log-table__header">
          <div>
            <div className="maintenance-log-table__title">维护执行日志</div>
            <div className="maintenance-log-table__note">只要执行过，就会在这里记录清理了什么、清了多少、是否异常。</div>
          </div>
        </div>

        {maintenanceLogStatus !== "success" ? (
          <AsyncContentView
            status={maintenanceLogStatus}
            loadingText="同步日志中..."
            error={error ?? undefined}
            emptyText="暂无维护日志"
          />
        ) : (
          <div className="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>板块</th>
                  <th>状态</th>
                  <th>清理范围</th>
                  <th>执行摘要</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                {logRows.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <div className="maintenance-log-table__job">{row.jobLabel}</div>
                      <div className="maintenance-log-table__trigger">{row.triggerLabel}</div>
                    </td>
                    <td>
                      <span className={`tag ${resolveToneTagClass(row.tone)}`}>{row.statusLabel}</span>
                      {row.errorDetail ? <div className="maintenance-log-table__error">{row.errorDetail}</div> : null}
                    </td>
                    <td>
                      <div className="maintenance-log-table__range">{row.rangeLabel}</div>
                    </td>
                    <td>
                      <div className="maintenance-log-table__summary">{row.summary}</div>
                      <div className="maintenance-log-table__message">{row.message}</div>
                    </td>
                    <td>{row.timeLabel}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <AdminDialog
        open={isRuleDialogOpen}
        title="规则设置"
        description="这里只保留商家能直接理解的业务清理项，保存后会同步用于自动清理和立即执行全部。"
        width={720}
        onClose={savingRules ? () => undefined : () => setIsRuleDialogOpen(false)}
        footer={(
          <>
            <button className="btn btn-outline" disabled={savingRules} onClick={() => setIsRuleDialogOpen(false)}>关闭</button>
            <button
              className="btn btn-primary"
              disabled={savingRules || loading || !hasUnsavedRuleChanges}
              onClick={() => handleSaveRules().then(() => setIsRuleDialogOpen(false)).catch(() => undefined)}
            >
              {savingRules ? "保存中..." : hasUnsavedRuleChanges ? "保存并应用规则" : "规则已保存"}
            </button>
          </>
        )}
      >
        <div className="maintenance-rule-dialog-grid">
          {visibleRules.map((rule) => {
            const statusTone = resolveToneTagClass(
              rule.lastStatus === "FAILED"
                ? "red"
                : rule.lastStatus === "PARTIAL_SUCCESS"
                  ? "orange"
                  : rule.lastStatus === "SUCCESS"
                    ? "green"
                    : "gray"
            );
            return (
              <article key={rule.moduleKey} className="maintenance-module-card">
                <div className="maintenance-module-card__header">
                  <div>
                    <div className="maintenance-module-card__title">{resolveBusinessRuleLabel(rule.moduleKey)}</div>
                    <div className="maintenance-module-card__meta">{resolveBusinessRuleDescription(rule.moduleKey)}</div>
                  </div>
                  <button
                    className="btn btn-outline"
                    disabled={runningModuleKey === rule.moduleKey || loading}
                    onClick={() => handleModuleCleanup(rule.moduleKey).catch(() => undefined)}
                  >
                    <Trash2 size={16} />
                    {runningModuleKey === rule.moduleKey ? "执行中..." : "立即执行"}
                  </button>
                </div>

                <div className="maintenance-module-card__controls">
                  <label className="maintenance-module-card__field">
                    <span>保留时长</span>
                    <div className="maintenance-module-card__input-wrap">
                      <SafeInput
                        className="form-control"
                        type="number"
                        min={1}
                        value={rule.retentionValue}
                        onValueChange={(value) => updateRule(rule.moduleKey, { retentionValue: Math.max(1, Number(value || 1)) })}
                      />
                      <span className="maintenance-module-card__unit">{rule.retentionUnit === "HOUR" ? "小时" : "天"}</span>
                    </div>
                  </label>

                  <label className="maintenance-module-card__switch">
                    <input
                      type="checkbox"
                      checked={rule.autoEnabled}
                      onChange={(e) => updateRule(rule.moduleKey, { autoEnabled: e.target.checked })}
                    />
                    <span>自动清理</span>
                  </label>
                </div>

                <div className="maintenance-module-card__summary">
                  <div className="maintenance-module-card__summary-label">
                    最近结果
                    {rule.lastStatus
                      ? <span className={`tag ${statusTone}`}>{resolveMaintenanceStatusLabel(rule.lastStatus)}</span>
                      : <span className="tag tag-gray">暂无记录</span>}
                  </div>
                  <div className="maintenance-module-card__summary-text">{rule.lastResultSummary || "还没有执行记录"}</div>
                  <div className="maintenance-module-card__summary-time">
                    {rule.lastRunAt ? `最近执行于 ${rule.lastRunAt}` : "保存规则后可立即手动执行"}
                  </div>
                </div>
              </article>
            );
          })}
          {visibleRules.length === 0 ? <div className="empty-state">当前还没有可配置的业务清理规则</div> : null}
        </div>
      </AdminDialog>

      <AdminDialog
        open={isCleanupConfirmOpen}
        title="确认执行全部清理"
        description="系统会按当前规则逐个板块执行清理，并写入维护日志。"
        width={520}
        onClose={cleaning ? () => undefined : () => setIsCleanupConfirmOpen(false)}
        footer={
          <>
            <button className="btn btn-outline" disabled={cleaning} onClick={() => setIsCleanupConfirmOpen(false)}>取消</button>
            <button className="btn-delete" disabled={cleaning} onClick={() => handleCleanup().catch(() => undefined)}>
              {cleaning ? "执行中..." : "确认执行"}
            </button>
          </>
        }
      >
        <div className="delete-confirm-details">
          <div className="delete-confirm-details__item">
            <span className="delete-confirm-details__label">执行范围：</span>
            <span className="delete-confirm-details__value">订单历史、回执记录、钱包流水</span>
          </div>
          <div className="delete-confirm-details__item">
            <span className="delete-confirm-details__label">执行方式：</span>
            <span className="delete-confirm-details__value">按当前保留时长逐项检查，并把每个板块的结果写入日志</span>
          </div>
        </div>
      </AdminDialog>
    </div>
  );
}
