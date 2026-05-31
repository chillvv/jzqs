import React, { useEffect, useMemo, useState } from "react";
import { Database, Trash2, ShieldCheck, CheckCircle } from "lucide-react";
import { fetchMaintenanceLogs, fetchMaintenanceOverview, triggerDataCleanup } from "../../shared/api/http";
import type { MaintenanceLogItemResponse, MaintenanceOverviewResponse } from "../../shared/api/types";
import { buildMaintenanceLogRows, type MaintenanceTone } from "./maintenancePage.helpers";
import { formatDateTimeLabel } from "../../shared/utils/dateTime";

const EMPTY_OVERVIEW: MaintenanceOverviewResponse = {
  latestManual: null,
  latestAuto: null,
  latestCloudReceipt: null,
  latestCloudStorage: null
};

function resolveToneTagClass(tone: MaintenanceTone) {
  if (tone === "green") return "tag-green";
  if (tone === "orange") return "tag-orange";
  if (tone === "red") return "tag-red";
  return "tag-gray";
}

export function MaintenancePage() {
  const [overview, setOverview] = useState<MaintenanceOverviewResponse>(EMPTY_OVERVIEW);
  const [logs, setLogs] = useState<MaintenanceLogItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [cleaning, setCleaning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<{ status: string; message: string } | null>(null);

  async function reloadMaintenanceData() {
    setLoading(true);
    try {
      const [nextOverview, nextLogs] = await Promise.all([
        fetchMaintenanceOverview(),
        fetchMaintenanceLogs()
      ]);
      setOverview(nextOverview);
      setLogs(nextLogs);
      setError(null);
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || String(err);
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    reloadMaintenanceData().catch((err) => window.alert(err?.response?.data?.message || err?.message || String(err)));
  }, []);

  async function handleCleanup() {
    if (!window.confirm("确定要执行数据清理吗？历史过期数据将被永久删除。")) {
      return;
    }

    setCleaning(true);
    try {
      const response = await triggerDataCleanup();
      setResult(response);
      await reloadMaintenanceData();
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err?.message || String(err));
    } finally {
      setCleaning(false);
    }
  }

  const logRows = useMemo(() => buildMaintenanceLogRows(logs), [logs]);
  
  // 提取最新的一次成功记录用于主控卡片展示
  const latestSuccess = overview.latestManual?.status === 'SUCCESS' ? overview.latestManual 
    : overview.latestAuto?.status === 'SUCCESS' ? overview.latestAuto 
    : null;

  return (
    <div className="customer-asset-page">
      <div className="page-header" style={{ marginBottom: "20px" }}>
        <div>
          <h2 className="page-title" style={{ fontSize: "20px" }}>系统维护</h2>
        </div>
      </div>

      {/* 极简主控卡片 */}
      <div className="admin-panel" style={{ 
        padding: "24px", 
        display: "flex", 
        alignItems: "center", 
        justifyContent: "space-between",
        background: "linear-gradient(to right, #ffffff, #f8fafc)",
        border: "1px solid rgba(148, 163, 184, 0.2)",
        boxShadow: "0 2px 10px rgba(0,0,0,0.02)"
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
          <div style={{ 
            width: "48px", height: "48px", 
            borderRadius: "12px", 
            background: "var(--primary-color)", 
            color: "white",
            display: "flex", alignItems: "center", justifyContent: "center" 
          }}>
            <Database size={24} />
          </div>
          <div>
            <div style={{ fontSize: "16px", fontWeight: 700, color: "var(--text-main)", marginBottom: "4px" }}>
              数据存储健康状态
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", fontSize: "13px", color: "var(--text-sub)" }}>
              {latestSuccess ? (
                <>
                  <ShieldCheck size={14} color="#16a34a" />
                  <span>最近清理于 {formatDateTimeLabel(latestSuccess.finishedAt || latestSuccess.startedAt)}</span>
                  <span style={{ color: "#cbd5e1" }}>|</span>
                  <span>释放 {latestSuccess.deletedCount} 条历史数据</span>
                </>
              ) : loading ? (
                <span>正在检查系统状态...</span>
              ) : (
                <span>等待首次清理以释放存储空间</span>
              )}
            </div>
          </div>
        </div>
        
        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          {result && (
            <div style={{ display: "flex", alignItems: "center", gap: "6px", color: "#16a34a", fontSize: "13px", fontWeight: 600, paddingRight: "12px" }}>
              <CheckCircle size={14} />
              {result.message}
            </div>
          )}
          <button 
            className="btn btn-primary" 
            style={{ padding: "8px 20px", borderRadius: "8px" }}
            disabled={cleaning} 
            onClick={() => handleCleanup().catch(() => undefined)}
          >
            <Trash2 size={16} />
            {cleaning ? "正在清理..." : "立即清理过期数据"}
          </button>
        </div>
      </div>

      {/* 极简日志列表 */}
      <div className="table-container" style={{ marginTop: "24px", border: "none", boxShadow: "none" }}>
        <div style={{ fontSize: "14px", fontWeight: 700, color: "var(--text-main)", marginBottom: "16px", paddingLeft: "4px" }}>
          维护日志记录
        </div>

        {error ? (
          <div className="empty-state" style={{ color: "var(--error-color)" }}>加载失败：{error}</div>
        ) : loading ? (
          <div className="empty-state">同步日志中...</div>
        ) : logRows.length === 0 ? (
          <div className="empty-state" style={{ background: "transparent" }}>暂无维护日志</div>
        ) : (
          <div className="table-responsive">
            <table style={{ borderCollapse: "separate", borderSpacing: "0 8px" }}>
              <thead>
                <tr style={{ color: "var(--text-sub)", fontSize: "12px" }}>
                  <th style={{ background: "transparent", borderBottom: "1px solid #f1f5f9", paddingBottom: "12px" }}>任务类型</th>
                  <th style={{ background: "transparent", borderBottom: "1px solid #f1f5f9", paddingBottom: "12px" }}>执行状态</th>
                  <th style={{ background: "transparent", borderBottom: "1px solid #f1f5f9", paddingBottom: "12px" }}>清理结果</th>
                  <th style={{ background: "transparent", borderBottom: "1px solid #f1f5f9", paddingBottom: "12px" }}>时间</th>
                </tr>
              </thead>
              <tbody>
                {logRows.map((row) => (
                  <tr key={row.id} style={{ background: "#fff", boxShadow: "0 1px 3px rgba(0,0,0,0.02)" }}>
                    <td style={{ padding: "12px 16px", borderRadius: "8px 0 0 8px" }}>
                      <div style={{ fontWeight: 600, color: "var(--text-main)", fontSize: "13px" }}>{row.jobLabel}</div>
                      <div style={{ color: "var(--text-sub)", fontSize: "12px", marginTop: "2px" }}>{row.triggerLabel}</div>
                    </td>
                    <td style={{ padding: "12px 16px" }}>
                      <span className={`tag ${resolveToneTagClass(row.tone)}`} style={{ padding: "2px 8px", fontSize: "11px" }}>
                        {row.statusLabel}
                      </span>
                      {row.errorDetail && <div style={{ color: "var(--error-color)", fontSize: "11px", marginTop: "4px" }}>{row.errorDetail}</div>}
                    </td>
                    <td style={{ padding: "12px 16px", fontSize: "13px", color: "var(--text-main)" }}>
                      {row.summary}
                    </td>
                    <td style={{ padding: "12px 16px", fontSize: "12px", color: "var(--text-sub)", borderRadius: "0 8px 8px 0" }}>
                      {row.timeLabel}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
