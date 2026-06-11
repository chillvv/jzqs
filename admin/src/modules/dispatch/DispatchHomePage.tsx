import React, { useEffect, useMemo, useState } from "react";
import { AlertTriangle, Search, Trash2 } from "lucide-react";
import {
  batchAssignDispatchPendingOrders,
  deleteOrder,
  fetchDispatchAreaBindings,
  fetchDispatchOverview,
  fetchDispatchPendingItems,
  fetchDispatchRiderProgress
} from "../../shared/api/http";
import type {
  BatchOperationResponse,
  DispatchAreaBindingResponse,
  DispatchOverviewResponse,
  DispatchPendingItemResponse,
  DispatchRiderProgressResponse
} from "../../shared/api/types";
import { AppSelect } from "../../shared/components/AppSelect";
import { AdminDialog } from "../../shared/components/AdminDialog";
import {
  buildDispatchBoardViewModel,
  buildDispatchPendingSearchText,
  DEFAULT_OPERATOR,
  normalizeDispatchAreaBindings,
  normalizeDispatchOverview,
  mealPeriodLabel
} from "./dispatchCenterLayout.helpers";
import { useDispatchContext } from "./DispatchContext";

const inputStyle: React.CSSProperties = {
  height: "36px",
  borderRadius: "10px",
  border: "1px solid var(--border-color)",
  padding: "0 10px",
  backgroundColor: "#fff",
  color: "var(--text-main)",
  width: "100%"
};

const selectStyle: React.CSSProperties = { width: "100%" };

function matchesPendingSearch(item: DispatchPendingItemResponse, search: string) {
  const keyword = search.trim().toLowerCase();
  if (!keyword) {
    return true;
  }
  return buildDispatchPendingSearchText(item).includes(keyword);
}

function dispatchOrderStatusLabel(status: string, isCurrent: boolean) {
  if (isCurrent) return "当前配送";
  switch (status) {
    case "DELIVERED":
      return "已送达";
    case "DISPATCHING":
      return "配送中";
    default:
      return "待送";
  }
}

export function DispatchHomePage() {
  const { serveDate, mealPeriod } = useDispatchContext();
  const [overview, setOverview] = useState<DispatchOverviewResponse>(normalizeDispatchOverview({}));
  const [pendingItems, setPendingItems] = useState<DispatchPendingItemResponse[]>([]);
  const [areaBindings, setAreaBindings] = useState<DispatchAreaBindingResponse[]>([]);
  const [riderProgress, setRiderProgress] = useState<DispatchRiderProgressResponse[]>([]);
  const [selectedRiderName, setSelectedRiderName] = useState<string>();
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [search, setSearch] = useState("");
  const [selectedPendingIds, setSelectedPendingIds] = useState<number[]>([]);
  const [inlineAreas, setInlineAreas] = useState<Record<number, string>>({});
  const [batchAreaCode, setBatchAreaCode] = useState("");
  const [batchAssigning, setBatchAssigning] = useState(false);
  const [batchResult, setBatchResult] = useState<BatchOperationResponse | null>(null);
  const [deleteConfirmState, setDeleteConfirmState] = useState<{ orderId: number; customerName: string } | null>(null);

  useEffect(() => {
    reloadAll().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)));
  }, [mealPeriod, serveDate]);

  const areaOptions = useMemo(
    () =>
      Array.from(new Set(areaBindings.map((binding) => binding.areaCode)))
        .sort((a, b) => a.localeCompare(b, "zh-CN"))
        .map((areaCode) => ({ label: areaCode, value: areaCode })),
    [areaBindings]
  );

  const pendingSearchItems = useMemo(
    () => pendingItems.filter((item) => matchesPendingSearch(item, search)),
    [pendingItems, search]
  );

  const selectedPendingSet = useMemo(() => new Set(selectedPendingIds), [selectedPendingIds]);

  const allVisibleSelected = pendingSearchItems.length > 0 && pendingSearchItems.every((item) => selectedPendingSet.has(item.orderId));
  const boardView = useMemo(
    () => buildDispatchBoardViewModel(riderProgress, areaBindings, { selectedRiderName, selectedOrderId }),
    [riderProgress, areaBindings, selectedRiderName, selectedOrderId]
  );

  async function reloadAll() {
    const results = await Promise.allSettled([
      fetchDispatchOverview(mealPeriod, serveDate),
      fetchDispatchRiderProgress(mealPeriod, serveDate),
      fetchDispatchAreaBindings(mealPeriod, serveDate),
      fetchDispatchPendingItems(mealPeriod, serveDate)
    ]);
    const [ovResult, progressResult, bindingsResult, pendingResult] = results;
    if (ovResult.status === "fulfilled") setOverview(normalizeDispatchOverview(ovResult.value));
    if (progressResult.status === "fulfilled") setRiderProgress(progressResult.value);
    if (bindingsResult.status === "fulfilled") setAreaBindings(normalizeDispatchAreaBindings(bindingsResult.value));
    if (pendingResult.status === "fulfilled") {
      setPendingItems(pendingResult.value);
      setSelectedPendingIds((prev) => prev.filter((id) => pendingResult.value.some((item: DispatchPendingItemResponse) => item.orderId === id)));
      setInlineAreas((prev) => Object.fromEntries(
        Object.entries(prev).filter(([key]) => pendingResult.value.some((item) => item.orderId === Number(key)))
      ));
    }
  }

  function togglePending(orderId: number) {
    setSelectedPendingIds((prev) => (
      prev.includes(orderId) ? prev.filter((id) => id !== orderId) : [...prev, orderId]
    ));
  }

  function toggleAllVisible() {
    const visibleIds = pendingSearchItems.map((item) => item.orderId);
    if (visibleIds.length === 0) {
      return;
    }
    setSelectedPendingIds((prev) => {
      if (visibleIds.every((id) => prev.includes(id))) {
        return prev.filter((id) => !visibleIds.includes(id));
      }
      return Array.from(new Set([...prev, ...visibleIds]));
    });
  }

  async function handleBatchAssign() {
    if (selectedPendingIds.length === 0) {
      window.alert("请先勾选待处理订单");
      return;
    }
    if (!batchAreaCode.trim()) {
      window.alert("请先选择区域");
      return;
    }
    setBatchAssigning(true);
    try {
      const result = await batchAssignDispatchPendingOrders({
        orderIds: selectedPendingIds,
        areaCode: batchAreaCode.trim(),
        updatedBy: DEFAULT_OPERATOR
      });
      setBatchResult(result);
      await reloadAll();
      setSelectedPendingIds([]);
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    } finally {
      setBatchAssigning(false);
    }
  }

  async function handleSingleAssign(orderId: number) {
    const areaCode = inlineAreas[orderId];
    if (!areaCode) {
      window.alert("请先选择区域");
      return;
    }
    setBatchAssigning(true);
    try {
      const result = await batchAssignDispatchPendingOrders({
        orderIds: [orderId],
        areaCode,
        updatedBy: DEFAULT_OPERATOR
      });
      setBatchResult(result);
      await reloadAll();
      setInlineAreas((prev) => {
        const next = { ...prev };
        delete next[orderId];
        return next;
      });
    } finally {
      setBatchAssigning(false);
    }
  }

  async function handleDeleteOrder(orderId: number, customerName: string) {
    setDeleteConfirmState({ orderId, customerName });
  }

  async function confirmDelete() {
    if (!deleteConfirmState) return;
    
    try {
      await deleteOrder(deleteConfirmState.orderId);
      setDeleteConfirmState(null);
      await reloadAll();
      window.alert('订单已删除');
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    }
  }

  return (
    <div className="admin-stack">
      <div className="toolbar">
        <div className="dispatch-toolbar">
          <div className="dispatch-toolbar__search">
            <Search size={14} className="dispatch-toolbar__search-icon" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="按客户名或地址搜索"
              style={{ ...inputStyle, paddingLeft: "34px" }}
            />
          </div>
        </div>
      </div>

      <div className="admin-grid-3" style={{ marginBottom: '16px' }}>
        <div className="admin-panel" style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '8px', borderLeft: '4px solid var(--warning-color)' }}>
          <div style={{ color: 'var(--text-light)', fontSize: '13px' }}>待分配订单</div>
          <div style={{ fontSize: '24px', fontWeight: 'bold', color: 'var(--warning-color)' }}>{overview.pendingCount} 单</div>
        </div>
        <div className="admin-panel" style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '8px', borderLeft: '4px solid var(--primary-color)' }}>
          <div style={{ color: 'var(--text-light)', fontSize: '13px' }}>待配送订单</div>
          <div style={{ fontSize: '24px', fontWeight: 'bold', color: 'var(--primary-color)' }}>{overview.dispatchingCount} 单</div>
        </div>
        <div className="admin-panel" style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '8px', borderLeft: '4px solid var(--error-color)' }}>
          <div style={{ color: 'var(--text-light)', fontSize: '13px' }}>区域缺骑手</div>
          <div style={{ fontSize: '24px', fontWeight: 'bold', color: 'var(--error-color)' }}>{overview.missingRiderAreaCount} 个区域</div>
        </div>
      </div>

      <div className="dispatch-section">
        <div className="dispatch-section__header">
          <div>
            <div className="dispatch-section__title">调度三栏</div>
            <div className="dispatch-section__note">
              左侧看骑手进度，中间看当前骑手队列，右侧直接看本单备注、地址参照图和本次送达图。
            </div>
          </div>
          <span className="tag tag-blue">{boardView.riderCards.length} 位骑手</span>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "280px minmax(0, 1fr) 360px", gap: "16px", alignItems: "start" }}>
          <section className="admin-panel" style={{ padding: "16px", display: "grid", gap: "12px" }}>
            <div className="dispatch-section__title">骑手进度</div>
            {boardView.riderCards.length === 0 ? (
              <div className="dispatch-empty" style={{ margin: 0 }}>当前没有可展示的骑手进度。</div>
            ) : (
              boardView.riderCards.map((item) => {
                const selected = boardView.activeRider?.key === item.key;
                return (
                  <button
                    key={item.key}
                    type="button"
                    className="dispatch-card"
                    onClick={() => {
                      setSelectedRiderName(item.riderName);
                      setSelectedOrderId(undefined);
                    }}
                    style={{
                      textAlign: "left",
                      border: selected ? "1px solid var(--primary-color)" : "1px solid var(--border-color)",
                      boxShadow: selected ? "0 0 0 2px rgba(59, 130, 246, 0.12)" : "none"
                    }}
                  >
                    <div className="dispatch-card__header">
                      <div>
                        <div className="dispatch-card__title">{item.riderName}</div>
                        <div className="dispatch-card__subtitle">{item.areaCode || "未分区"}</div>
                      </div>
                      <span className={`tag ${item.exceptionCount > 0 ? "tag-red" : "tag-green"}`}>
                        异常 {item.exceptionCount}
                      </span>
                    </div>
                    <div className="dispatch-chip-list" style={{ marginTop: "12px" }}>
                      <span className="tag tag-blue">已送 {item.completedCount} / {item.totalCount}</span>
                      <span className="tag tag-amber">待送 {item.pendingCount}</span>
                    </div>
                    <div className="dispatch-inline-note" style={{ marginTop: "10px" }}>
                      当前第 {item.currentSequenceNumber ?? "-"} 单，下一单 #{item.nextOrderId ?? "-"}
                    </div>
                  </button>
                );
              })
            )}
          </section>

          <section className="admin-panel" style={{ padding: "16px", display: "grid", gap: "12px" }}>
            <div className="dispatch-section__title">当前骑手队列</div>
            <div className="dispatch-section__note">
              {boardView.activeRider
                ? `${boardView.activeRider.riderName} · ${boardView.activeRider.areaCode}`
                : "选择左侧骑手后查看队列"}
            </div>
            {boardView.queueOrders.length === 0 ? (
              <div className="dispatch-empty" style={{ margin: 0 }}>当前骑手暂无队列订单。</div>
            ) : (
              <div style={{ display: "grid", gap: "10px" }}>
                {boardView.queueOrders.map((order) => {
                  const isCurrent = order.orderId === boardView.activeRider?.currentOrderId;
                  const isSelected = order.orderId === boardView.activeOrder?.orderId;
                  return (
                    <button
                      key={order.orderId}
                      type="button"
                      onClick={() => setSelectedOrderId(order.orderId)}
                      style={{
                        textAlign: "left",
                        borderRadius: "14px",
                        border: isSelected ? "1px solid var(--primary-color)" : "1px solid var(--border-color)",
                        background: isSelected ? "rgba(59, 130, 246, 0.08)" : "#fff",
                        padding: "12px",
                        display: "grid",
                        gap: "8px"
                      }}
                    >
                      <div style={{ display: "flex", justifyContent: "space-between", gap: "12px", alignItems: "center" }}>
                        <div>
                          <strong>#{order.sequenceNumber} {order.customerName}</strong>
                          <div className="dispatch-inline-note">{order.deliveryAddress}</div>
                        </div>
                        <span className={`tag ${isCurrent ? "tag-blue" : order.deliveryStatus === "DELIVERED" ? "tag-green" : "tag-gray"}`}>
                          {dispatchOrderStatusLabel(order.deliveryStatus, isCurrent)}
                        </span>
                      </div>
                      <div className="dispatch-chip-list">
                        <span className="tag tag-gray">用户备注 {order.userNote ? "有" : "无"}</span>
                        <span className="tag tag-gray">商家备注 {order.adminNote ? "有" : "无"}</span>
                        <span className="tag tag-gray">参照图 {order.referenceImageUrl ? "有" : "无"}</span>
                        <span className="tag tag-gray">送达图 {order.receiptUrl ? "有" : "无"}</span>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </section>

          <section className="admin-panel" style={{ padding: "16px", display: "grid", gap: "12px" }}>
            <div className="dispatch-section__title">订单详情</div>
            {boardView.activeOrder ? (
              <>
                <div>
                  <strong>{boardView.activeOrder.customerName}</strong>
                  <div className="dispatch-inline-note">{boardView.activeOrder.deliveryAddress}</div>
                </div>
                <div style={{ display: "grid", gap: "10px" }}>
                  <div>
                    <div className="admin-panel-note">用户备注</div>
                    <div>{boardView.activeOrder.userNote || "-"}</div>
                  </div>
                  <div>
                    <div className="admin-panel-note">商家备注</div>
                    <div>{boardView.activeOrder.adminNote || "-"}</div>
                  </div>
                  <div>
                    <div className="admin-panel-note">回单说明</div>
                    <div>{boardView.activeOrder.receiptNote || "-"}</div>
                  </div>
                </div>
                <div style={{ display: "grid", gap: "12px" }}>
                  <div>
                    <div className="admin-panel-note" style={{ marginBottom: "8px" }}>地址参照图</div>
                    {boardView.activeOrder.referenceImageUrl ? (
                      <img
                        src={boardView.activeOrder.referenceImageUrl}
                        alt="地址参照图"
                        style={{ width: "100%", borderRadius: "12px", border: "1px solid var(--border-color)" }}
                      />
                    ) : (
                      <div className="dispatch-empty" style={{ margin: 0 }}>暂无地址参照图</div>
                    )}
                  </div>
                  <div>
                    <div className="admin-panel-note" style={{ marginBottom: "8px" }}>本次送达图</div>
                    {boardView.activeOrder.receiptUrl ? (
                      <img
                        src={boardView.activeOrder.receiptUrl}
                        alt="本次送达图"
                        style={{ width: "100%", borderRadius: "12px", border: "1px solid var(--border-color)" }}
                      />
                    ) : (
                      <div className="dispatch-empty" style={{ margin: 0 }}>暂无本次送达图</div>
                    )}
                  </div>
                </div>
              </>
            ) : (
              <div className="dispatch-empty" style={{ margin: 0 }}>当前没有可展示的订单详情。</div>
            )}
          </section>
        </div>
      </div>

      <div className="dispatch-section">
        <div className="dispatch-section__header">
          <div>
            <div className="dispatch-section__title">
              <AlertTriangle size={16} />
              待分配订单
            </div>
            <div className="dispatch-section__note">
              这里的派单结果会直接影响顾客端“待配送”状态和骑手端队列承接。当前只显示{mealPeriodLabel(mealPeriod)}真正需要人工介入的新客户或新地址订单，历史记忆订单会自动归区，不进入工作台。
            </div>
          </div>
          <span className="tag tag-amber">{pendingItems.length} 单</span>
        </div>

        <div className={`dispatch-bulk-bar${selectedPendingIds.length === 0 ? " is-idle" : ""}`}>
          <div className="dispatch-bulk-bar__summary">
            <div className="dispatch-bulk-bar__title">批量归入区域</div>
            <div className="dispatch-bulk-bar__note">
              {selectedPendingIds.length > 0
                ? `已选 ${selectedPendingIds.length} 单，选择区域后即可统一归入。`
                : "先勾选待分配订单，再统一归入区域。"}
            </div>
          </div>
          <div className="dispatch-bulk-bar__controls">
            <div className="dispatch-bulk-bar__field">
              <AppSelect
                value={batchAreaCode}
                options={[{ label: "分配区域 ▾", value: "" }, ...areaOptions]}
                onChange={setBatchAreaCode}
                style={selectStyle}
              />
            </div>
            <button
              className="btn btn-primary"
              disabled={!selectedPendingIds.length || !batchAreaCode.trim() || batchAssigning}
              onClick={() => handleBatchAssign()}
            >
              归入区域
            </button>
          </div>
        </div>

        {batchResult ? (
          <div className="dispatch-batch-result">
            <div className="dispatch-batch-result__header" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div className="dispatch-batch-result__title">批量处理结果</div>
              <button className="btn btn-outline btn-compact" onClick={() => setBatchResult(null)}>清除</button>
            </div>
            <div className="dispatch-batch-result__summary">
              成功 {batchResult.successCount} 单，失败 {batchResult.failureCount} 单
            </div>
            {batchResult.failures.length > 0 ? (
              <div className="dispatch-batch-result__list">
                {batchResult.failures.map((failure) => (
                  <div key={`${failure.targetId}-${failure.code}`} className="dispatch-batch-result__item">
                    订单 #{failure.targetId}：{failure.message}
                  </div>
                ))}
              </div>
            ) : null}
          </div>
        ) : null}

        {pendingSearchItems.length === 0 ? (
          <div className="dispatch-empty">
            {pendingItems.length === 0 ? "当前没有待分配订单，工作台已归整完成。" : "当前搜索条件下没有匹配的待分配订单。"}
          </div>
        ) : (
          <div className="dispatch-pending-shell">
            <div className="dispatch-pending-meta">
              <div className="dispatch-inline-note">已展示 {pendingSearchItems.length} / {pendingItems.length} 单，可跨区域连续勾选处理。</div>
              <button className="btn btn-outline btn-compact" onClick={toggleAllVisible}>
                {allVisibleSelected ? "取消当前结果" : "全选当前结果"}
              </button>
            </div>
            <div className="dispatch-pending-table-wrap">
              <table className="dispatch-pending-table">
                <thead>
                  <tr>
                    <th style={{ width: '40px' }}>
                      <input type="checkbox" checked={allVisibleSelected} onChange={toggleAllVisible} />
                    </th>
                    <th>客户</th>
                    <th>配送地址</th>
                    <th>分配区域</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingSearchItems.map((item, index) => (
                      <tr
                        key={`${item.orderId}-${index}`}
                      className={selectedPendingSet.has(item.orderId) ? "is-selected" : ""}
                      onClick={() => togglePending(item.orderId)}
                      style={{ cursor: "pointer" }}
                    >
                      <td onClick={(e) => e.stopPropagation()}>
                        <input
                          type="checkbox"
                          checked={selectedPendingSet.has(item.orderId)}
                          onChange={() => togglePending(item.orderId)}
                        />
                      </td>
                      <td>
                        <div className="admin-table-cell">
                          <strong>{item.customerName}</strong>
                          <span className="tag tag-amber">待分配</span>
                        </div>
                      </td>
                      <td className="dispatch-pending-address">{item.deliveryAddress}</td>
                      <td onClick={(e) => e.stopPropagation()}>
                        <AppSelect
                          value={inlineAreas[item.orderId] || ""}
                          options={[{ label: "选择区域 ▾", value: "" }, ...areaOptions]}
                          onChange={(val) => setInlineAreas(prev => ({ ...prev, [item.orderId]: val }))}
                          style={{ minWidth: '140px' }}
                        />
                      </td>
                      <td onClick={(e) => e.stopPropagation()}>
                        {selectedPendingSet.has(item.orderId) && (
                          <div className="dispatch-pending-actions">
                            <button
                              className="btn btn-primary btn-compact"
                              disabled={!inlineAreas[item.orderId] || batchAssigning}
                              onClick={() => handleSingleAssign(item.orderId)}
                            >
                              分配
                            </button>
                            <button
                              className="btn-delete btn-compact"
                              onClick={() => handleDeleteOrder(item.orderId, item.customerName)}
                            >
                              <Trash2 size={14} />
                              删除
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* 删除确认对话框 */}
        <AdminDialog
          open={Boolean(deleteConfirmState)}
          title="⚠️ 删除订单"
          width={500}
          onClose={() => setDeleteConfirmState(null)}
          footer={
            <div style={{ display: "flex", gap: "12px", justifyContent: "flex-end" }}>
              <button className="btn btn-outline" onClick={() => setDeleteConfirmState(null)}>取消</button>
              <button 
                className="btn-delete"
                onClick={() => confirmDelete().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}
              >
                <Trash2 size={16} />
                确认删除
              </button>
            </div>
          }
        >
          {deleteConfirmState && (
            <div style={{ display: "grid", gap: "16px" }}>
              <div className="delete-confirm-details">
                <div className="delete-confirm-details__item">
                  <span className="delete-confirm-details__label">客户：</span>
                  <span className="delete-confirm-details__value">{deleteConfirmState.customerName}</span>
                </div>
                <div className="delete-confirm-details__item">
                  <span className="delete-confirm-details__label">订单ID：</span>
                  <span className="delete-confirm-details__value">{deleteConfirmState.orderId}</span>
                </div>
              </div>
            </div>
          )}
        </AdminDialog>
      </div>
    </div>
  );
}
