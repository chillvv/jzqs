import React, { useEffect, useMemo, useState } from "react";
import { LifeBuoy, Plus, RotateCcw, Search } from "lucide-react";
import {
  createAftersaleCase,
  fetchAftersaleOrderOptions,
  fetchAftersales,
  resolveAftersaleCase
} from "../../shared/api/http";
import type {
  AdminAftersaleItemResponse,
  AdminAftersaleOrderOptionResponse
} from "../../shared/api/types";
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
  resolveSettlementSummary,
  resolveAftersaleTone,
  resolveAftersaleTypeLabel,
  resolveMealPeriodLabel,
  type AftersaleResolveAction,
  type AftersaleStatusKey
} from "./aftersalePage.helpers";
import { formatLocalDateInputValue } from "../../shared/utils/dateTime";

const DEFAULT_OPERATOR = "后台客服";

function getToday() {
  return formatLocalDateInputValue();
}

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
  const [viewMode, setViewMode] = useState<"ledger" | "settlement">("settlement");
  const [items, setItems] = useState<AdminAftersaleItemResponse[]>([]);
  const [orderOptions, setOrderOptions] = useState<AdminAftersaleOrderOptionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [activeStatus, setActiveStatus] = useState<AftersaleStatusKey | "ALL">("PENDING");
  const [hideAutoRefund, setHideAutoRefund] = useState(true);
  const [selectedCase, setSelectedCase] = useState<AdminAftersaleItemResponse | null>(null);
  const [resolveForm, setResolveForm] = useState(() => buildAftersaleResolveFormState("REFUND"));
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [savingCreate, setSavingCreate] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [createForm, setCreateForm] = useState({
    serveDate: getToday(),
    orderId: "",
    type: "COMPENSATION",
    reasonCode: "ADMIN_DIRECT",
    reasonText: "",
    issueParamSummary: "",
    estimatedLossMeals: 0,
    remark: ""
  });

  async function reloadList(
    nextView = viewMode,
    nextStatus: AftersaleStatusKey | "ALL" = activeStatus,
    nextType = typeFilter,
    nextStartDate = startDate,
    nextEndDate = endDate,
    nextHideAutoRefund = hideAutoRefund
  ) {
    setLoading(true);
    try {
      const response = await fetchAftersales({
        status: nextStatus === "ALL" ? undefined : nextStatus,
        type: nextType === "ALL" ? undefined : nextType,
        startDate: nextStartDate || undefined,
        endDate: nextEndDate || undefined,
        view: nextView,
        hideAutoRefund: nextHideAutoRefund
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
  }, [viewMode]);

  useEffect(() => {
    if (!showCreateModal) {
      return;
    }
    fetchAftersaleOrderOptions(createForm.serveDate)
      .then((response) => setOrderOptions(response))
      .catch((err: any) => {
        toast(err?.response?.data?.message || err?.message || "加载订单选项失败", "error");
      });
  }, [createForm.serveDate, showCreateModal]);

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
        keyword,
        hideAutoRefund
      }),
    [activeStatus, hideAutoRefund, items, keyword, typeFilter]
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

  function openCreateModal() {
    setCreateForm({
      serveDate: getToday(),
      orderId: "",
      type: "COMPENSATION",
      reasonCode: "ADMIN_DIRECT",
      reasonText: "",
      issueParamSummary: "",
      estimatedLossMeals: 0,
      remark: ""
    });
    setOrderOptions([]);
    setShowCreateModal(true);
  }

  function selectResolveAction(action: AftersaleResolveAction) {
    setResolveForm((current) => ({
      ...current,
      action,
      walletDelta: action === "REJECT" || action === "REGISTER_ONLY" ? 0 : Math.max(current.walletDelta, 1)
    }));
  }

  async function handleCreateSubmit() {
    if (!createForm.orderId) {
      toast("请选择订单", "error");
      return;
    }
    if (!createForm.reasonText.trim()) {
      toast("请填写售后原因", "error");
      return;
    }
    setSavingCreate(true);
    try {
      await createAftersaleCase({
        orderId: Number(createForm.orderId),
        type: createForm.type,
        reasonCode: createForm.reasonCode,
        reasonText: createForm.reasonText.trim(),
        issueParamSummary: createForm.issueParamSummary.trim(),
        estimatedLossMeals: Math.max(0, Number(createForm.estimatedLossMeals) || 0),
        sourceCategory: "NORMAL",
        remark: createForm.remark.trim(),
        operatorName: DEFAULT_OPERATOR
      });
      toast("售后已登记");
      setShowCreateModal(false);
      await reloadList("ledger", "ALL");
      setViewMode("ledger");
      setActiveStatus("ALL");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "售后登记失败", "error");
    } finally {
      setSavingCreate(false);
    }
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
        settledLossMeals: Math.max(0, Number(resolveForm.settledLossMeals) || 0),
        giftZeroMealCount: Math.max(0, Number(resolveForm.giftZeroMealCount) || 0),
        giftVeggieJuiceCount: Math.max(0, Number(resolveForm.giftVeggieJuiceCount) || 0),
        adminRemark: resolveForm.adminRemark.trim(),
        operatorName: DEFAULT_OPERATOR
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
          <h2 className="page-title">售后台账</h2>
          <p className="page-subtitle">按登记和处理双视图管理售后台账、秒退款与补偿结算</p>
        </div>
        <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
          <button className="btn btn-primary" onClick={openCreateModal}>
            <Plus size={16} />
            登记售后
          </button>
          <button className="btn btn-outline" onClick={() => reloadList().catch(() => undefined)}>
            <LifeBuoy size={16} />
            刷新
          </button>
        </div>
      </div>

      <div className="stat-row">
        <button
          type="button"
          className="stat-card"
          onClick={() => {
            setViewMode("ledger");
            setActiveStatus("ALL");
          }}
          style={{
            textAlign: "left",
            borderColor: viewMode === "ledger" ? "rgba(37, 99, 235, 0.35)" : undefined,
            boxShadow: viewMode === "ledger" ? "0 0 0 2px rgba(37, 99, 235, 0.08)" : undefined
          }}
        >
          <div className="stat-title">售后登记</div>
          <div className="stat-val">{items.length}</div>
        </button>
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            className="stat-card"
            onClick={() => setActiveStatus(tab.key)}
            style={{
              textAlign: "left",
              opacity: viewMode === "settlement" || tab.key === "ALL" ? 1 : 0.92,
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
            <span className="filter-label">开始:</span>
            <DatePicker value={startDate} onChange={(value) => setStartDate(value)} showTomorrowShortcut={false} />
          </div>
          <div className="filter-item">
            <span className="filter-label">结束:</span>
            <DatePicker value={endDate} onChange={(value) => setEndDate(value)} showTomorrowShortcut={false} />
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
          <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: "13px", fontWeight: 600 }}>
            <input
              type="checkbox"
              checked={hideAutoRefund}
              onChange={(event) => setHideAutoRefund(event.target.checked)}
            />
            隐藏秒退款
          </label>
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
          <button className="btn btn-primary" onClick={() => reloadList().catch(() => undefined)}>
            <Search size={16} />
            查询
          </button>
          <button
            className="btn btn-outline"
            onClick={() => {
              setStartDate("");
              setEndDate("");
              setTypeFilter("ALL");
              setKeyword("");
              setHideAutoRefund(true);
              const nextStatus = viewMode === "settlement" ? "PENDING" : "ALL";
              setActiveStatus(nextStatus);
              reloadList(viewMode, nextStatus, "ALL", "", "", true).catch(() => undefined);
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
            <span>{viewMode === "ledger" ? "登记台账" : "处理台账"}</span>
            <span style={{ color: "var(--text-sub)", fontSize: "13px" }}>顶部卡片可直接切换登记视图和处理视图</span>
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
                    <th>问题摘要</th>
                    <th>损耗 / 结算</th>
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
                            {resolveAftersaleSourceLabel(item.sourceCategory === "AUTO_REFUND" ? "AUTO_REFUND" : item.source)}
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
                          {item.issueParamSummary ? (
                            <span style={{ color: "var(--text-sub)", fontSize: "12px" }}>{item.issueParamSummary}</span>
                          ) : null}
                          {item.refundBlocking && <span className="tag tag-red">退款拦单</span>}
                        </div>
                      </td>
                      <td>
                        <div style={{ display: "grid", gap: "4px" }}>
                          <span>预估损耗 {item.estimatedLossMeals}</span>
                          <span style={{ color: "var(--text-sub)", fontSize: "12px" }}>
                            {resolveSettlementSummary(item)}
                          </span>
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
                    <div className="mobile-card-value">
                      {item.reasonText || "-"}
                      {item.issueParamSummary ? ` / ${item.issueParamSummary}` : ""}
                    </div>
                  </div>
                  <div className="mobile-card-row">
                    <div className="mobile-card-label">结算</div>
                    <div className="mobile-card-value">{resolveSettlementSummary(item)}</div>
                  </div>
                  <div className="mobile-card-row">
                    <div className="mobile-card-label">申请</div>
                    <div className="mobile-card-value">
                      {resolveAftersaleSourceLabel(item.sourceCategory === "AUTO_REFUND" ? "AUTO_REFUND" : item.source)} · {item.requestedAt || "-"}
                    </div>
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

      {showCreateModal && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: "760px" }}>
            <div className="modal-header">
              <span>登记售后</span>
              <button type="button" className="modal-close" onClick={savingCreate ? undefined : () => setShowCreateModal(false)} disabled={savingCreate}>
                ×
              </button>
            </div>
            <div className="modal-body" style={{ display: "grid", gap: "18px" }}>
              <div className="auth-panel">
                <div className="auth-panel__title">选择订单</div>
                <div className="auth-panel__grid">
                  <div>
                    <strong>配送日期</strong>
                    <span>
                      <DatePicker
                        value={createForm.serveDate}
                        onChange={(value) => setCreateForm((current) => ({ ...current, serveDate: value, orderId: "" }))}
                        showTomorrowShortcut={false}
                      />
                    </span>
                  </div>
                  <div>
                    <strong>订单</strong>
                    <span>
                      <AppSelect
                        className="app-select--filter"
                        style={{ width: "100%" }}
                        value={createForm.orderId}
                        options={orderOptions.map((item) => ({
                          label: `#${item.orderId} ${item.customerName} ${resolveMealPeriodLabel(item.mealPeriod)} ${item.addressSummary || ""}`.trim(),
                          value: String(item.orderId)
                        }))}
                        onChange={(value) => setCreateForm((current) => ({ ...current, orderId: value }))}
                        showSearch
                      />
                    </span>
                  </div>
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">售后类型</label>
                <div className="action-chip-row">
                  <button
                    type="button"
                    className={`action-chip ${createForm.type === "COMPENSATION" ? "active" : ""}`}
                    onClick={() => setCreateForm((current) => ({ ...current, type: "COMPENSATION" }))}
                  >
                    补偿
                  </button>
                  <button
                    type="button"
                    className={`action-chip ${createForm.type === "REFUND" ? "active" : ""}`}
                    onClick={() => setCreateForm((current) => ({ ...current, type: "REFUND" }))}
                  >
                    退款
                  </button>
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">售后原因</label>
                <textarea
                  className="form-control"
                  value={createForm.reasonText}
                  onChange={(event) => setCreateForm((current) => ({ ...current, reasonText: event.target.value }))}
                  rows={3}
                  placeholder="请输入售后问题描述"
                />
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">问题参数摘要</label>
                <input
                  className="form-control"
                  value={createForm.issueParamSummary}
                  onChange={(event) => setCreateForm((current) => ({ ...current, issueParamSummary: event.target.value }))}
                  placeholder="例如：午餐 / 少饭 / 漏送"
                />
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">预估损耗餐数</label>
                <input
                  className="form-control"
                  type="number"
                  min="0"
                  value={createForm.estimatedLossMeals}
                  onChange={(event) => setCreateForm((current) => ({
                    ...current,
                    estimatedLossMeals: Math.max(0, Number(event.target.value) || 0)
                  }))}
                />
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">登记备注</label>
                <textarea
                  className="form-control"
                  value={createForm.remark}
                  onChange={(event) => setCreateForm((current) => ({ ...current, remark: event.target.value }))}
                  rows={3}
                  placeholder="补充处理背景或对账说明"
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setShowCreateModal(false)} disabled={savingCreate}>
                取消
              </button>
              <button className="btn btn-primary" onClick={() => handleCreateSubmit().catch(() => undefined)} disabled={savingCreate}>
                {savingCreate ? "提交中..." : "确认登记"}
              </button>
            </div>
          </div>
        </div>
      )}

      {selectedCase && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: "720px" }}>
            <div className="modal-header">
              <span>售后处理</span>
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
                  <div><strong>问题摘要</strong><span>{selectedCase.issueParamSummary || "-"}</span></div>
                  <div><strong>申请来源</strong><span>{resolveAftersaleSourceLabel(selectedCase.sourceCategory === "AUTO_REFUND" ? "AUTO_REFUND" : selectedCase.source)}</span></div>
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

              {resolveForm.action !== "REJECT" && (
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label">结算信息</label>
                  <div style={{ display: "grid", gap: "12px", gridTemplateColumns: "repeat(2, minmax(0, 1fr))" }}>
                    <div className="form-group" style={{ marginBottom: 0 }}>
                      <label className="form-label">餐次数量</label>
                      <input
                        className="form-control"
                        type="number"
                        min={resolveForm.action === "REGISTER_ONLY" ? "0" : "1"}
                        value={resolveForm.walletDelta}
                        onChange={(event) => setResolveForm((current) => ({
                          ...current,
                          walletDelta: resolveForm.action === "REGISTER_ONLY"
                            ? Math.max(0, Number(event.target.value) || 0)
                            : Math.max(1, Number(event.target.value) || 1)
                        }))}
                      />
                    </div>
                    <div className="form-group" style={{ marginBottom: 0 }}>
                      <label className="form-label">结算损耗餐数</label>
                      <input
                        className="form-control"
                        type="number"
                        min="0"
                        value={resolveForm.settledLossMeals}
                        onChange={(event) => setResolveForm((current) => ({
                          ...current,
                          settledLossMeals: Math.max(0, Number(event.target.value) || 0)
                        }))}
                      />
                    </div>
                    <div className="form-group" style={{ marginBottom: 0 }}>
                      <label className="form-label">补零餐</label>
                      <input
                        className="form-control"
                        type="number"
                        min="0"
                        value={resolveForm.giftZeroMealCount}
                        onChange={(event) => setResolveForm((current) => ({
                          ...current,
                          giftZeroMealCount: Math.max(0, Number(event.target.value) || 0)
                        }))}
                      />
                    </div>
                    <div className="form-group" style={{ marginBottom: 0 }}>
                      <label className="form-label">果蔬汁</label>
                      <input
                        className="form-control"
                        type="number"
                        min="0"
                        value={resolveForm.giftVeggieJuiceCount}
                        onChange={(event) => setResolveForm((current) => ({
                          ...current,
                          giftVeggieJuiceCount: Math.max(0, Number(event.target.value) || 0)
                        }))}
                      />
                    </div>
                  </div>
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
