import React, { useEffect, useMemo, useState } from "react";
import { ArrowLeft, X } from "lucide-react";
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

        <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: "16px", alignItems: "start" }}>
          {!boardView.activeRider ? (
            <section className="admin-panel" style={{ padding: "20px", display: "grid", gap: "16px" }}>
              <div className="dispatch-section__title" style={{ marginBottom: "8px" }}>骑手一览</div>
              {boardView.riderCards.length === 0 ? (
                <div className="dispatch-empty" style={{ margin: 0 }}>当前没有可展示的骑手进度。</div>
              ) : (
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "16px" }}>
                  {boardView.riderCards.map((item) => {
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
                          border: "1px solid var(--border-color)",
                          transition: "all 0.2s"
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
                        <div className="dispatch-chip-list" style={{ marginTop: "16px" }}>
                          <span className="tag tag-blue">已送 {item.completedCount} / {item.totalCount}</span>
                          <span className="tag tag-amber">待送 {item.pendingCount}</span>
                        </div>
                        <div className="dispatch-inline-note" style={{ marginTop: "12px", paddingTop: "12px", borderTop: "1px solid var(--border-color)" }}>
                          当前第 {item.currentSequenceNumber ?? "-"} 单，下一单 #{item.nextOrderId ?? "-"}
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </section>
          ) : (
            <section className="admin-panel" style={{ padding: "20px", display: "grid", gap: "16px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "12px", borderBottom: "1px solid var(--border-color)", paddingBottom: "16px" }}>
                <button 
                  className="btn btn-outline btn-compact" 
                  onClick={() => { setSelectedRiderName(undefined); setSelectedOrderId(undefined); }}
                >
                  <ArrowLeft size={16} />
                  返回一览
                </button>
                <div className="dispatch-section__title" style={{ margin: 0 }}>
                  {boardView.activeRider.riderName} 的配送队列
                </div>
                <div className="tag tag-gray">{boardView.activeRider.areaCode}</div>
              </div>
              
              {boardView.queueOrders.length === 0 ? (
                <div className="dispatch-empty" style={{ margin: 0 }}>当前骑手暂无队列订单。</div>
              ) : (
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))", gap: "16px" }}>
                  {boardView.queueOrders.map((order) => {
                    const isCurrent = order.orderId === boardView.activeRider?.currentOrderId;
                    return (
                      <button
                        key={order.orderId}
                        type="button"
                        onClick={() => setSelectedOrderId(order.orderId)}
                        style={{
                          textAlign: "left",
                          borderRadius: "14px",
                          border: "1px solid var(--border-color)",
                          background: "#fff",
                          padding: "16px",
                          display: "grid",
                          gap: "12px",
                          boxShadow: "0 2px 8px rgba(0,0,0,0.02)"
                        }}
                      >
                        <div style={{ display: "flex", justifyContent: "space-between", gap: "12px", alignItems: "flex-start" }}>
                          <div>
                            <strong style={{ fontSize: "16px", display: "block", marginBottom: "4px" }}>#{order.sequenceNumber} {order.customerName}</strong>
                            <div className="dispatch-inline-note">{order.deliveryAddress}</div>
                          </div>
                          <span className={`tag ${isCurrent ? "tag-blue" : order.deliveryStatus === "DELIVERED" ? "tag-green" : "tag-gray"}`} style={{ flexShrink: 0 }}>
                            {dispatchOrderStatusLabel(order.deliveryStatus, isCurrent)}
                          </span>
                        </div>
                        <div className="dispatch-chip-list">
                          {order.userNote && <span className="tag tag-gray">用户备注</span>}
                          {order.adminNote && <span className="tag tag-gray">商家备注</span>}
                          {order.referenceImageUrl && <span className="tag tag-gray">参照图</span>}
                          {order.receiptUrl && <span className="tag tag-gray">送达图</span>}
                        </div>
                        <div style={{ color: "var(--primary-color)", fontSize: "13px", fontWeight: 500, marginTop: "4px" }}>
                          查看详情及图片 →
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </section>
          )}
        </div>
      </div>

      {boardView.activeOrder && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ width: "600px", maxWidth: "90vw" }}>
            <div className="modal-header">
              <span>订单详情 - #{boardView.activeOrder.sequenceNumber} {boardView.activeOrder.customerName}</span>
              <span className="modal-close" onClick={() => setSelectedOrderId(undefined)}><X size={20} /></span>
            </div>
            <div className="modal-body" style={{ maxHeight: "70vh", overflowY: "auto", padding: "20px" }}>
              <div style={{ display: "grid", gap: "20px" }}>
                <div className="admin-panel" style={{ padding: "16px", background: "var(--surface-container)" }}>
                  <div className="admin-panel-note" style={{ marginBottom: "8px" }}>配送地址</div>
                  <strong style={{ fontSize: "16px" }}>{boardView.activeOrder.deliveryAddress}</strong>
                </div>

                <div style={{ display: "grid", gap: "12px" }}>
                  <div className="admin-panel" style={{ padding: "16px" }}>
                    <div className="admin-panel-note" style={{ marginBottom: "8px" }}>用户备注</div>
                    <div>{boardView.activeOrder.userNote || "-"}</div>
                  </div>
                  <div className="admin-panel" style={{ padding: "16px" }}>
                    <div className="admin-panel-note" style={{ marginBottom: "8px" }}>商家备注</div>
                    <div>{boardView.activeOrder.adminNote || "-"}</div>
                  </div>
                  <div className="admin-panel" style={{ padding: "16px" }}>
                    <div className="admin-panel-note" style={{ marginBottom: "8px" }}>回单说明</div>
                    <div>{boardView.activeOrder.receiptNote || "-"}</div>
                  </div>
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px" }}>
                  <div>
                    <div className="admin-panel-note" style={{ marginBottom: "8px" }}>地址参照图</div>
                    {boardView.activeOrder.referenceImageUrl ? (
                      <img
                        src={boardView.activeOrder.referenceImageUrl}
                        alt="地址参照图"
                        style={{ width: "100%", borderRadius: "12px", border: "1px solid var(--border-color)", objectFit: "cover", aspectRatio: "3/4" }}
                      />
                    ) : (
                      <div className="dispatch-empty" style={{ margin: 0, height: "200px", display: "flex", alignItems: "center", justifyContent: "center" }}>暂无参照图</div>
                    )}
                  </div>
                  <div>
                    <div className="admin-panel-note" style={{ marginBottom: "8px" }}>本次送达图</div>
                    {boardView.activeOrder.receiptUrl ? (
                      <img
                        src={boardView.activeOrder.receiptUrl}
                        alt="本次送达图"
                        style={{ width: "100%", borderRadius: "12px", border: "1px solid var(--border-color)", objectFit: "cover", aspectRatio: "3/4" }}
                      />
                    ) : (
                      <div className="dispatch-empty" style={{ margin: 0, height: "200px", display: "flex", alignItems: "center", justifyContent: "center" }}>暂无送达图</div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
