import { useCallback, useEffect, useState } from "react";
import { Search } from "lucide-react";
import {
  fetchAdminOperationLogs,
  extractAdminApiErrorMessage
} from "../../shared/api/http";
import type { AdminOperationLogItem } from "../../shared/api/types";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../../shared/components/ui/table";
import { AppSelect } from "../../shared/components/AppSelect";
import { DatePicker } from "../../shared/components/DatePicker";
import { SafeInput } from "../../shared/components/SafeInput";
import { toast } from "../../shared/components/Toast";

const MODULE_OPTIONS = [
  { label: "全部模块", value: "" },
  { label: "客户资产（余额/加扣餐）", value: "CUSTOMER_ASSET" },
  { label: "订单运营", value: "ORDER" },
  { label: "售后处理", value: "AFTERSALE" },
  { label: "菜单配置", value: "MENU_WEEK" },
  { label: "排菜计划", value: "MENU_SCHEDULE" },
  { label: "套餐发放", value: "PACKAGE_PLAN" },
  { label: "骑手调度", value: "DISPATCH" },
  { label: "系统设置", value: "SETTINGS" },
  { label: "后台账号", value: "ADMIN_USER" },
  { label: "系统维护", value: "MAINTENANCE" },
  { label: "订阅规则", value: "SUBSCRIPTION_RULE" },
  { label: "登录尝试", value: "AUTH" }
];

const STATUS_OPTIONS = [
  { label: "全部状态", value: "" },
  { label: "成功", value: "SUCCESS" },
  { label: "失败", value: "FAILED" }
];

const PAGE_SIZE = 20;

export function OperationLogsPage() {
  const [items, setItems] = useState<AdminOperationLogItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [operatorName, setOperatorName] = useState("");
  const [module, setModule] = useState("");
  const [status, setStatus] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const loadLogs = useCallback(
    async (targetPage: number) => {
      setLoading(true);
      try {
        const result = await fetchAdminOperationLogs({
          page: targetPage,
          pageSize: PAGE_SIZE,
          operatorName: operatorName.trim() || undefined,
          module: module || undefined,
          status: status || undefined,
          startDate: startDate || undefined,
          endDate: endDate || undefined
        });
        setItems(result.items || []);
        setTotal(result.total || 0);
        setPage(targetPage);
      } catch (err) {
        toast(extractAdminApiErrorMessage(err, "加载操作日志失败"), "error");
      } finally {
        setLoading(false);
      }
    },
    [operatorName, module, status, startDate, endDate]
  );

  useEffect(() => {
    loadLogs(1).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleSearch() {
    loadLogs(1).catch(() => undefined);
  }

  return (
    <div className="customer-asset-page">
      <div className="page-header">
        <div>
          <h2 className="page-title">操作日志</h2>
          <p className="page-subtitle">
            后台所有写操作自动记录操作人，出现问题时可直接追溯。点击任意一行可展开技术详情
          </p>
        </div>
      </div>

      <section className="customer-detail-card customer-detail-card--full">
        <div className="filter-row" style={{ marginBottom: 14 }}>
          <div className="filter-item">
            <span className="filter-label">操作人:</span>
            <SafeInput
              className="input-box"
              style={{ width: 180 }}
              value={operatorName}
              onValueChange={setOperatorName}
              placeholder="姓名/手机号"
            />
          </div>
          <div className="filter-item">
            <span className="filter-label">模块:</span>
            <AppSelect
              value={module}
              options={MODULE_OPTIONS}
              onChange={setModule}
              style={{ width: 200 }}
            />
          </div>
          <div className="filter-item">
            <span className="filter-label">状态:</span>
            <AppSelect
              value={status}
              options={STATUS_OPTIONS}
              onChange={setStatus}
              style={{ width: 130 }}
            />
          </div>
          <div className="filter-item">
            <span className="filter-label">开始:</span>
            <DatePicker value={startDate} onChange={(value) => setStartDate(value)} showTomorrowShortcut={false} />
          </div>
          <div className="filter-item">
            <span className="filter-label">结束:</span>
            <DatePicker value={endDate} onChange={(value) => setEndDate(value)} showTomorrowShortcut={false} />
          </div>
          <button type="button" className="btn btn-primary" onClick={handleSearch}>
            <Search size={14} /> 查询
          </button>
        </div>

        {loading ? (
          <div style={{ padding: 24, textAlign: "center", color: "var(--text-muted)" }}>加载中...</div>
        ) : items.length === 0 ? (
          <div style={{ padding: 24, textAlign: "center", color: "var(--text-muted)" }}>暂无操作日志</div>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>时间</TableHead>
                  <TableHead>操作人</TableHead>
                  <TableHead>干了什么</TableHead>
                  <TableHead>对谁操作的</TableHead>
                  <TableHead>结果</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((log) => (
                  <TableRow
                    key={log.id}
                    style={{ cursor: "pointer" }}
                    onClick={() => setExpandedId(expandedId === log.id ? null : log.id)}
                  >
                    <TableCell>
                      <span className="customer-transaction-time">{log.createdAt}</span>
                    </TableCell>
                    <TableCell>
                      <div style={{ fontWeight: 600 }}>{log.operatorName || "-"}</div>
                      {log.operatorPhone && log.operatorPhone !== log.operatorName && (
                        <div style={{ fontSize: 12, color: "var(--text-muted)" }}>{log.operatorPhone}</div>
                      )}
                    </TableCell>
                    <TableCell>
                      <div style={{ fontWeight: 600 }}>
                        {log.actionLabel || log.action || "-"}
                        {log.actionLabel && log.moduleLabel ? (
                          <span style={{ fontWeight: 400, color: "var(--text-muted)", fontSize: 12 }}>（{log.moduleLabel}）</span>
                        ) : null}
                      </div>
                      {log.detailLabel && (
                        <div style={{ fontSize: 12, color: "var(--text-muted)" }}>{log.detailLabel}</div>
                      )}
                    </TableCell>
                    <TableCell>{log.targetLabel || "-"}</TableCell>
                    <TableCell>
                      <span
                        style={{
                          color: log.status === "SUCCESS" ? "#16a34a" : "#dc2626",
                          fontWeight: 600
                        }}
                      >
                        {log.status === "SUCCESS" ? "成功" : "失败"}
                      </span>
                      {log.errorMessage && (
                        <div style={{ fontSize: 12, color: "#dc2626" }}>{log.errorMessage}</div>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {items
              .filter((log) => log.id === expandedId)
              .map((log) => (
                <div
                  key={`detail-${log.id}`}
                  style={{
                    margin: "10px 0",
                    padding: 12,
                    background: "var(--bg-sub, #f8fafc)",
                    borderRadius: 8,
                    fontSize: 13
                  }}
                >
                  <div style={{ fontWeight: 600, marginBottom: 6 }}>技术详情（供排查使用）</div>
                  <div style={{ marginBottom: 4 }}>
                    <span style={{ color: "var(--text-muted)" }}>操作人：</span>
                    {log.operatorName || "-"}
                    {log.operatorPhone ? `（${log.operatorPhone}）` : ""}
                  </div>
                  <div style={{ marginBottom: 4 }}>
                    <span style={{ color: "var(--text-muted)" }}>模块/动作：</span>
                    {log.module} / {log.action}
                  </div>
                  <div style={{ marginBottom: 4 }}>
                    <span style={{ color: "var(--text-muted)" }}>接口：</span>
                    {log.httpMethod} {log.requestPath}
                  </div>
                  <div style={{ marginBottom: 4 }}>
                    <span style={{ color: "var(--text-muted)" }}>参数：</span>
                    <span style={{ wordBreak: "break-all", fontFamily: "monospace" }}>
                      {log.requestSummary || "（无）"}
                    </span>
                  </div>
                  <div style={{ marginBottom: 4 }}>
                    <span style={{ color: "var(--text-muted)" }}>来源IP：</span>
                    {log.clientIp || "-"}
                  </div>
                  <div>
                    <span style={{ color: "var(--text-muted)" }}>耗时：</span>
                    {log.durationMs}ms
                  </div>
                </div>
              ))}

            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 12 }}>
              <span style={{ color: "var(--text-muted)", fontSize: 13 }}>共 {total} 条</span>
              <div style={{ display: "flex", gap: 8 }}>
                <button
                  type="button"
                  className="btn btn-outline"
                  disabled={page <= 1}
                  onClick={() => loadLogs(page - 1).catch(() => undefined)}
                >
                  上一页
                </button>
                <span style={{ lineHeight: "32px", fontSize: 13 }}>
                  {page} / {totalPages}
                </span>
                <button
                  type="button"
                  className="btn btn-outline"
                  disabled={page >= totalPages}
                  onClick={() => loadLogs(page + 1).catch(() => undefined)}
                >
                  下一页
                </button>
              </div>
            </div>
          </>
        )}
      </section>
    </div>
  );
}
