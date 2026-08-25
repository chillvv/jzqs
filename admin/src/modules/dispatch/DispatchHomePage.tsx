import React, { useEffect, useMemo, useState } from "react";
import { AlertTriangle, Search } from "lucide-react";
import {
  batchAssignDispatchPendingOrders,
  extractAdminApiErrorMessage
} from "../../shared/api/http";
import type {
  BatchOperationResponse,
  DispatchPendingItemResponse
} from "../../shared/api/types";
import { AppSelect } from "../../shared/components/AppSelect";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { SafeInput } from "../../shared/components/SafeInput";
import { toast } from "../../shared/components/Toast";
import {
  buildDispatchBoardViewModel,
  buildDispatchPendingSearchText,
  DEFAULT_OPERATOR,
  mealPeriodLabel
} from "./dispatchCenterLayout.helpers";
import { useDispatchContext } from "./DispatchContext";
import { useDispatchHomeLiveData } from "./dispatchLiveData";

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

function getErrorMessage(error: unknown, fallback = "操作失败") {
  return extractAdminApiErrorMessage(error, fallback);
}

export function DispatchHomePage() {
  const { serveDate, mealPeriod } = useDispatchContext();
  const {
    overview,
    pendingItems,
    areaBindings,
    loading: loadingAll,
    warning: reloadWarning,
    loadError,
    reload: reloadAll
  } = useDispatchHomeLiveData({ serveDate, mealPeriod });
  const [search, setSearch] = useState("");
  const [selectedPendingIds, setSelectedPendingIds] = useState<number[]>([]);
  const [batchAreaCode, setBatchAreaCode] = useState("");
  const [batchAssigning, setBatchAssigning] = useState(false);
  const [batchResult, setBatchResult] = useState<BatchOperationResponse | null>(null);

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

  useEffect(() => {
    setSelectedPendingIds((prev) => prev.filter((id) => pendingItems.some((item) => item.orderId === id)));
  }, [pendingItems]);

  useEffect(() => {
    if (!loadError) {
      return;
    }
    toast(getErrorMessage(loadError, "加载分单工作台失败"), "error");
  }, [loadError]);

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
      toast("请先勾选待处理订单", "error");
      return;
    }
    if (!batchAreaCode.trim()) {
      toast("请先选择区域", "error");
      return;
    }
    setBatchAssigning(true);
    try {
      const result = await batchAssignDispatchPendingOrders({
        orderIds: selectedPendingIds,
        areaCode: batchAreaCode.trim()
      });
      setBatchResult(result);
      await reloadAll();
      setSelectedPendingIds([]);
      toast(`已归入区域 ${result.successCount} 单`);
    } catch (err: any) {
      toast(getErrorMessage(err, "批量归入区域失败"), "error");
    } finally {
      setBatchAssigning(false);
    }
  }

  return (
    <div className="admin-stack">
      <div className="dispatch-section">
        {loadingAll ? (
          <div className="admin-panel" style={{ marginBottom: "12px", color: "var(--text-muted)" }}>
            分单数据加载中...
          </div>
        ) : null}
        {reloadWarning ? (
          <div className="admin-panel" style={{ marginBottom: "12px", borderLeft: "4px solid var(--warning-color)", color: "var(--warning-color-dark)" }}>
            {reloadWarning}
          </div>
        ) : null}
        <div className="dispatch-section__header">
          <div>
            <div className="dispatch-section__title">
              <AlertTriangle size={16} />
              分单工作台
            </div>
            <div className="dispatch-section__note">
              这里的派单结果会直接影响顾客端“待配送”状态和骑手端队列承接。当前只显示{mealPeriodLabel(mealPeriod)}真正需要人工介入的新客户或新地址订单，历史记忆订单会自动归区，不进入工作台。
            </div>
          </div>
          <span className="tag tag-amber">{pendingItems.length} 单</span>
        </div>

        {pendingSearchItems.length === 0 ? (
          <div className="dispatch-empty">
            {pendingItems.length === 0 ? "当前没有待分配订单，工作台已归整完成。" : "当前搜索条件下没有匹配的待分配订单。"}
          </div>
        ) : (
          <div className="dispatch-pending-shell dispatch-pending-shell--sticky">
            <div className={`dispatch-bulk-bar dispatch-bulk-bar--sticky${selectedPendingIds.length === 0 ? " is-idle" : ""}`}>
              <div className="dispatch-bulk-bar__summary">
                <div className="dispatch-bulk-bar__title">批量归入区域</div>
                <div className="dispatch-bulk-bar__note">
                  {selectedPendingIds.length > 0
                    ? `已选 ${selectedPendingIds.length} 单，选择区域后即可统一归入。`
                    : "先勾选待分配订单，再统一归入区域。"}
                </div>
              </div>
              <div className="dispatch-bulk-bar__controls">
                <div className="dispatch-bulk-bar__actions">
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
                    归区
                  </button>
                </div>
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
            <div className="dispatch-pending-meta dispatch-pending-meta--sticky">
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
                        <div className="dispatch-pending-actions">
                          {selectedPendingSet.has(item.orderId) ? <span className="dispatch-pending-action-hint">已勾选，使用顶部批量归区</span> : null}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
