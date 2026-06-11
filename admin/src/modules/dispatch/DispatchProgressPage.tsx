import React, { useEffect, useMemo, useState } from "react";
import { fetchDispatchAreaBindings, fetchDispatchRiderProgress } from "../../shared/api/http";
import type { DispatchAreaBindingResponse, DispatchRiderProgressResponse } from "../../shared/api/types";
import { buildDispatchBoardViewModel, normalizeDispatchAreaBindings } from "./dispatchCenterLayout.helpers";
import { useDispatchContext } from "./DispatchContext";

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

export function DispatchProgressPage() {
  const { serveDate, mealPeriod } = useDispatchContext();
  const [areaBindings, setAreaBindings] = useState<DispatchAreaBindingResponse[]>([]);
  const [riderProgress, setRiderProgress] = useState<DispatchRiderProgressResponse[]>([]);
  const [selectedRiderName, setSelectedRiderName] = useState<string>();
  const [selectedOrderId, setSelectedOrderId] = useState<number>();

  useEffect(() => {
    reloadAll().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)));
  }, [mealPeriod, serveDate]);

  async function reloadAll() {
    const results = await Promise.allSettled([
      fetchDispatchRiderProgress(mealPeriod, serveDate),
      fetchDispatchAreaBindings(mealPeriod, serveDate),
    ]);
    const [progressResult, bindingsResult] = results;
    if (progressResult.status === "fulfilled") setRiderProgress(progressResult.value);
    if (bindingsResult.status === "fulfilled") setAreaBindings(normalizeDispatchAreaBindings(bindingsResult.value));
  }

  const boardView = useMemo(
    () => buildDispatchBoardViewModel(riderProgress, areaBindings, { selectedRiderName, selectedOrderId }),
    [riderProgress, areaBindings, selectedRiderName, selectedOrderId]
  );

  return (
    <div className="admin-stack">
      <div className="dispatch-section">
        <div className="dispatch-section__header">
          <div>
            <div className="dispatch-section__title">骑手进度</div>
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
    </div>
  );
}
