import React, { useEffect, useMemo, useState } from "react";
import { LifeBuoy, RotateCcw, Search } from "lucide-react";
import { fetchAftersales, resolveAftersaleCase } from "../../shared/api/http";
import type { AdminAftersaleItemResponse } from "../../shared/api/types";
import { toast } from "../../shared/components/Toast";
import { AppSelect } from "../../shared/components/AppSelect";
import { DatePicker } from "../../shared/components/DatePicker";
import {
  buildAftersaleResolveFormState,
  buildAftersaleTabs,
  buildAftersaleView,
  countAftersalesByStatus,
  resolveAftersaleAvailableActions,
  resolveAftersaleCompactStatusLabel,
  resolveAftersaleSourceLabel,
  resolveAftersaleStatusLabel,
  resolveAftersaleTone,
  resolveAftersaleTypeLabel,
  resolveMealPeriodLabel,
  type AftersaleResolveAction,
  type AftersaleStatusKey
} from "./aftersalePage.helpers";

const DEFAULT_DATE = "";

function resolveToneTagClass(status: string, type: string) {
  const tone = resolveAftersaleTone(status, type);
  if (tone === "green") {
    return "tag-green";
  }
  if (tone === "blue") {
    return "tag-blue";
  }
  if (tone === "red") {
    return "tag-red";
  }
  if (tone === "orange") {
    return "tag-orange";
  }
  return "tag-gray";
}

export function AftersalePage() {
  const [items, setItems] = useState<AdminAftersaleItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState("");
  const [serveDate, setServeDate] = useState(DEFAULT_DATE);
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [activeStatus, setActiveStatus] = useState<AftersaleStatusKey | "ALL">("PENDING");
  const [selectedCase, setSelectedCase] = useState<AdminAftersaleItemResponse | null>(null);
  const [resolveForm, setResolveForm] = useState(() => buildAftersaleResolveFormState("REFUND"));
  const [submitting, setSubmitting] = useState(false);

  async function reloadList(nextServeDate = serveDate, nextType = typeFilter) {
    setLoading(true);
    try {
      const response = await fetchAftersales({
        serveDate: nextServeDate || undefined,
        type: nextType === "ALL" ? undefined : nextType
      });
      setItems(response);
      setError(null);
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || String(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    reloadList().catch(() => undefined);
  }, []);

  const tabs = useMemo(
    () =>
      buildAftersaleTabs(
        countAftersalesByStatus(items, "PENDING"),
        countAftersalesByStatus(items, "PROCESSING"),
        countAftersalesByStatus(items, "COMPLETED"),
        countAftersalesByStatus(items, "REJECTED")
      ),
    [items]
  );

  const filteredItems = useMemo(
    () =>
      buildAftersaleView(items, {
        status: activeStatus,
        type: typeFilter,
        keyword
      }),
    [activeStatus, items, keyword, typeFilter]
  );

  const availableActions = selectedCase
    ? resolveAftersaleAvailableActions(selectedCase.type, selectedCase.status)
    : [];

  function openResolveModal(item: AdminAftersaleItemResponse) {
    setSelectedCase(item);
    setResolveForm(buildAftersaleResolveFormState(item.type));
  }

  function closeResolveModal() {
    setSelectedCase(null);
    setResolveForm(buildAftersaleResolveFormState("REFUND"));
  }

  function selectResolveAction(action: AftersaleResolveAction) {
    setResolveForm((current) => ({
      ...current,
      action,
      walletDelta: action === "REJECT" || action === "REGISTER_ONLY" ? 0 : Math.max(current.walletDelta, 1)
    }));
  }

  async function handleResolveSubmit() {
    if (!selectedCase) {
      return;
    }
    if (!resolveForm.adminRemark.trim()) {
      toast("请填写处理备注", "error");
      return;
    }
    setSubmitting(true);
    try {
      await resolveAftersaleCase(selectedCase.id, {
        resolutionAction: resolveForm.action,
        refundBlocking: resolveForm.refundBlocking,
        walletDelta: resolveForm.action === "REJECT" || resolveForm.action === "REGISTER_ONLY" ? 0 : Math.max(resolveForm.walletDelta, 1),
        adminRemark: resolveForm.adminRemark.trim(),
        operatorName: "后台客服"
      });
      toast("售后已处理");
      closeResolveModal();
      await reloadList();
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "售后处理失败", "error");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="customer-asset-page">
      <div className="page-header">
        <div>
          <h2 className="page-title">统一售后中心</h2>
        </div>
        <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
          <button className="btn btn-outline" onClick={() => reloadList().catch(() => undefined)}>
            <LifeBuoy size={16} />
            刷新
          </button>
        </div>
      </div>

      <div className="stat-row">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            className="stat-card"
            onClick={() => setActiveStatus(tab.key)}
            style={{
              textAlign: "left",
              borderColor: activeStatus === tab.key ? "rgba(37, 99, 235, 0.35)" : undefined,
              boxShadow: activeStatus === tab.key ? "0 0 0 2px rgba(37, 99, 235, 0.08)" : undefined
            }}
          >
            <div className="stat-title">{tab.label}</div>
            <div className="stat-val">{tab.count}</div>
          </button>
        ))}
      </div>

      <div className="toolbar">
        <div className="filter-row">
          <div className="filter-item">
            <span className="filter-label">日期:</span>
            <DatePicker value={serveDate} onChange={(value) => setServeDate(value)} showTomorrowShortcut={false} />
          </div>
          <div className="filter-item">
            <span className="filter-label">类型:</span>
            <AppSelect
              className="app-select--filter"
              style={{ width: "140px" }}
              value={typeFilter}
              options={[
                { label: "全部类型", value: "ALL" },
                { label: "退款", value: "REFUND" },
                { label: "补偿", value: "COMPENSATION" }
              ]}
              onChange={(value) => setTypeFilter(value)}
            />
          </div>
          <div className="filter-item">
            <span className="filter-label">搜索:</span>
            <input
              type="text"
              className="input-box"
              placeholder="客户/手机号/原因"
              style={{ width: "220px" }}
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
          </div>
          <button className="btn btn-primary" onClick={() => reloadList(serveDate, typeFilter).catch(() => undefined)}>
            <Search size={16} />
            查询
          </button>
          <button
            className="btn btn-outline"
            onClick={() => {
              setServeDate(DEFAULT_DATE);
              setTypeFilter("ALL");
              setKeyword("");
              setActiveStatus("PENDING");
              reloadList(DEFAULT_DATE, "ALL").catch(() => undefined);
            }}
          >
            <RotateCcw size={16} />
            重置
          </button>
          <div style={{ marginLeft: "auto", color: "var(--text-sub)", fontSize: "13px", fontWeight: 600 }}>
            {filteredItems.length} 条
          </div>
        </div>
      </div>

      <div className="table-container">
        <div className="table-header-toolbar">
          <div style={{ display: "grid", gap: "8px" }}>
            <span>售后单</span>
          </div>
        </div>

        {error ? (
          <div className="empty-state" style={{ color: "var(--error-color)" }}>
            数据加载失败：{error}
          </div>
        ) : loading ? (
          <div className="empty-state">加载中...</div>
        ) : filteredItems.length === 0 ? (
          <div className="empty-state">当前筛选条件下没有售后记录</div>
        ) : (
          <>
            <div className="table-responsive">
              <table>
                <thead>
                  <tr>
                    <th>客户</th>
                    <th>售后类型</th>
                    <th>用餐时间</th>
                    <th>原因</th>
                    <th>申请时间</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredItems.map((item) => {
                    const availableItemActions = resolveAftersaleAvailableActions(item.type, item.status);
                    return (
                    <tr key={item.id}>
                      <td>
                        <div style={{ display: "grid", gap: "4px" }}>
                          <span style={{ fontWeight: 700 }}>{item.customerName}</span>
                          <span style={{ color: "var(--text-sub)" }}>{item.customerPhone}</span>
                        </div>
                      </td>
                      <td>
                        <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", gap: "6px" }}>
                          <span className={`tag ${item.type === "REFUND" ? "tag-orange" : "tag-blue"}`}>
                            {resolveAftersaleTypeLabel(item.type)}
                          </span>
                          <span style={{ color: "var(--text-sub)", fontSize: "12px" }}>
                            {resolveAftersaleSourceLabel(item.source)}
                          </span>
                        </div>
                      </td>
                      <td>
                        <div style={{ display: "grid", gap: "4px" }}>
                          <span>{item.serveDate}</span>
                          <span style={{ color: "var(--text-sub)", fontSize: "12px" }}>
                            {resolveMealPeriodLabel(item.mealPeriod)}
                          </span>
                        </div>
                      </td>
                      <td>
                        <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", gap: "6px", maxWidth: "200px" }}>
                          <span>{item.reasonText || "-"}</span>
                          {item.refundBlocking && <span className="tag tag-red">退款拦单</span>}
                        </div>
                      </td>
                      <td>
                        <span style={{ color: "var(--text-sub)", fontSize: "13px" }}>{item.requestedAt || "-"}</span>
                      </td>
                      <td>
                        <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", gap: "6px" }}>
                          <span className={`tag ${resolveToneTagClass(item.status, item.type)}`}>
                            {resolveAftersaleCompactStatusLabel(item.status)}
                          </span>
                          {item.processedAt && (
                            <span style={{ color: "var(--text-sub)", fontSize: "12px" }}>
                              {item.processedAt}
                            </span>
                          )}
                        </div>
                      </td>
                      <td>
                        {availableItemActions.length > 0 ? (
                          <button className="btn btn-outline" onClick={() => openResolveModal(item)}>
                            处理
                          </button>
                        ) : (
                          <span style={{ color: "var(--text-sub)", fontSize: "12px" }}>已结案</span>
                        )}
                      </td>
                    </tr>
                  )})}
                </tbody>
              </table>
            </div>

            <div className="mobile-card-list">
              {filteredItems.map((item) => {
                const availableItemActions = resolveAftersaleAvailableActions(item.type, item.status);
                return (
                <div className="mobile-card" key={item.id}>
                  <div className="mobile-card-header">
                    <div>
                      <span style={{ fontWeight: 700 }}>{item.customerName}</span>
                      <span style={{ color: "var(--text-sub)", fontSize: "12px", marginLeft: "8px" }}>{item.customerPhone}</span>
                    </div>
                    <span className={`tag ${resolveToneTagClass(item.status, item.type)}`}>
                      {resolveAftersaleCompactStatusLabel(item.status)}
                    </span>
                  </div>
                  <div className="mobile-card-row">
                    <div className="mobile-card-label">时间</div>
                    <div className="mobile-card-value">{item.serveDate} {resolveMealPeriodLabel(item.mealPeriod)}</div>
                  </div>
                  <div className="mobile-card-row">
                    <div className="mobile-card-label">类型</div>
                    <div className="mobile-card-value">
                      {resolveAftersaleTypeLabel(item.type)}
                      {item.refundBlocking ? " / 退款拦单" : ""}
                    </div>
                  </div>
                  <div className="mobile-card-row">
                    <div className="mobile-card-label">原因</div>
                    <div className="mobile-card-value">{item.reasonText || "-"}</div>
                  </div>
                  <div className="mobile-card-row">
                    <div className="mobile-card-label">申请</div>
                    <div className="mobile-card-value">{resolveAftersaleSourceLabel(item.source)} · {item.requestedAt || "-"}</div>
                  </div>
                  {item.processedAt ? (
                    <div className="mobile-card-row">
                      <div className="mobile-card-label">处理</div>
                      <div className="mobile-card-value">处理于 {item.processedAt}</div>
                    </div>
                  ) : null}
                  <div className="mobile-card-footer">
                    {availableItemActions.length > 0 ? (
                      <button className="btn btn-outline" onClick={() => openResolveModal(item)}>
                        处理
                      </button>
                    ) : (
                      <span style={{ color: "var(--text-sub)", fontSize: "12px" }}>已结案</span>
                    )}
                  </div>
                </div>
              )})}
            </div>
          </>
        )}
      </div>

      {selectedCase && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: "720px" }}>
            <div className="modal-header">
              <span>处理售后</span>
              <button type="button" className="modal-close" onClick={submitting ? undefined : closeResolveModal} disabled={submitting}>
                ×
              </button>
            </div>
            <div className="modal-body" style={{ display: "grid", gap: "18px" }}>
              <div className="auth-panel">
                <div className="auth-panel__title">售后信息</div>
                <div className="auth-panel__grid">
                  <div><strong>客户</strong><span>{selectedCase.customerName} / {selectedCase.customerPhone}</span></div>
                  <div><strong>用餐时间</strong><span>{selectedCase.serveDate} {resolveMealPeriodLabel(selectedCase.mealPeriod)}</span></div>
                  <div><strong>售后类型</strong><span>{resolveAftersaleTypeLabel(selectedCase.type)}</span></div>
                  <div><strong>当前状态</strong><span>{resolveAftersaleCompactStatusLabel(selectedCase.status)}</span></div>
                  <div><strong>原因</strong><span>{selectedCase.reasonText || "-"}</span></div>
                  <div><strong>申请来源</strong><span>{resolveAftersaleSourceLabel(selectedCase.source)}</span></div>
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">处理动作</label>
                <div className="action-chip-row">
                  {availableActions.map((action) => (
                    <button
                      key={action}
                      type="button"
                      className={`action-chip ${resolveForm.action === action ? "active" : ""}`}
                      onClick={() => selectResolveAction(action)}
                    >
                      {resolveActionLabel(action)}
                    </button>
                  ))}
                </div>
              </div>

              {resolveForm.action === "COMPENSATE_MEALS" && (
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label">餐次数量</label>
                  <input
                    className="form-control"
                    type="number"
                    min="1"
                    value={resolveForm.walletDelta}
                    onChange={(event) => setResolveForm((current) => ({
                      ...current,
                      walletDelta: Math.max(1, Number(event.target.value) || 1)
                    }))}
                  />
                </div>
              )}

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">后台备注</label>
                <textarea
                  className="form-control"
                  value={resolveForm.adminRemark}
                  onChange={(event) => setResolveForm((current) => ({ ...current, adminRemark: event.target.value }))}
                  placeholder="请填写退款原因、补偿说明或异常结论"
                  rows={4}
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={closeResolveModal} disabled={submitting}>
                取消
              </button>
              <button className="btn btn-primary" onClick={() => handleResolveSubmit().catch(() => undefined)} disabled={submitting}>
                {submitting ? "处理中..." : "确认处理"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function resolveActionLabel(action: AftersaleResolveAction) {
  if (action === "REFUND_TO_WALLET") {
    return "退款退回餐次";
  }
  if (action === "COMPENSATE_MEALS") {
    return "补回餐次";
  }
  if (action === "REGISTER_ONLY") {
    return "仅登记异常";
  }
  return "驳回";
}
