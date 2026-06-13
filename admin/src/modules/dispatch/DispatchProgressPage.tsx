import React, { useEffect, useMemo, useState } from "react";
import { MapPinned, PackageCheck, Truck, UserRound } from "lucide-react";
import { fetchDispatchAreaBindings, fetchDispatchRiderProgress } from "../../shared/api/http";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { toast } from "../../shared/components/Toast";
import type { DispatchAreaBindingResponse, DispatchRiderProgressResponse } from "../../shared/api/types";
import { hasDisplayValue, hasOrderAttention, normalizeDispatchAreaBindings } from "./dispatchCenterLayout.helpers";
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

type ProgressGroup = {
  key: string;
  areaCode: string;
  riderName: string;
  totalCount: number;
  completedCount: number;
  pendingCount: number;
  exceptionCount: number;
  currentOrderId: number | null;
  currentSequenceNumber: number | null;
  nextOrderId: number | null;
  missingRider: boolean;
  orders: DispatchAreaBindingResponse["orders"];
};

export function DispatchProgressPage() {
  const { serveDate, mealPeriod } = useDispatchContext();
  const [areaBindings, setAreaBindings] = useState<DispatchAreaBindingResponse[]>([]);
  const [riderProgress, setRiderProgress] = useState<DispatchRiderProgressResponse[]>([]);
  const [selectedGroupKey, setSelectedGroupKey] = useState<string>();
  const [selectedOrderId, setSelectedOrderId] = useState<number>();

  useEffect(() => {
    reloadAll().catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"));
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

  const progressGroups = useMemo<ProgressGroup[]>(() => {
    const groups = new Map<string, ProgressGroup>();

    areaBindings.forEach((binding) => {
      const matchedProgress =
        riderProgress.find((item) => item.areaCode === binding.areaCode && item.riderName === binding.currentRiderName) ??
        riderProgress.find((item) => item.areaCode === binding.areaCode && item.riderName === binding.defaultRiderName) ??
        riderProgress.find((item) => item.areaCode === binding.areaCode);

      const riderName =
        matchedProgress?.riderName ??
        binding.currentRiderName ??
        binding.defaultRiderName ??
        "待分配骑手";

      const orders = [...binding.orders].sort((left, right) => left.sequenceNumber - right.sequenceNumber);
      const deliveredCount = orders.filter((item) => item.deliveryStatus === "DELIVERED").length;
      const totalCount = matchedProgress?.totalCount ?? orders.length;
      const completedCount = matchedProgress?.completedCount ?? deliveredCount;
      const pendingCount = matchedProgress?.pendingCount ?? Math.max(totalCount - completedCount, 0);
      const key = `${binding.areaCode}::${riderName}`;

      groups.set(key, {
        key,
        areaCode: binding.areaCode,
        riderName,
        totalCount,
        completedCount,
        pendingCount,
        exceptionCount: matchedProgress?.exceptionCount ?? 0,
        currentOrderId: matchedProgress?.currentOrderId ?? null,
        currentSequenceNumber: matchedProgress?.currentSequenceNumber ?? null,
        nextOrderId: matchedProgress?.nextOrderId ?? null,
        missingRider: binding.missingRider,
        orders
      });
    });

    riderProgress.forEach((item) => {
      const key = `${item.areaCode}::${item.riderName}`;
      if (groups.has(key)) return;
      groups.set(key, {
        key,
        areaCode: item.areaCode || "未分区",
        riderName: item.riderName,
        totalCount: item.totalCount,
        completedCount: item.completedCount,
        pendingCount: item.pendingCount,
        exceptionCount: item.exceptionCount,
        currentOrderId: item.currentOrderId,
        currentSequenceNumber: item.currentSequenceNumber,
        nextOrderId: item.nextOrderId,
        missingRider: false,
        orders: []
      });
    });

    return Array.from(groups.values()).sort((left, right) => {
      if (left.missingRider !== right.missingRider) return left.missingRider ? 1 : -1;
      if (right.pendingCount !== left.pendingCount) return right.pendingCount - left.pendingCount;
      return left.areaCode.localeCompare(right.areaCode, "zh-CN") || left.riderName.localeCompare(right.riderName, "zh-CN");
    });
  }, [areaBindings, riderProgress]);

  useEffect(() => {
    if (selectedGroupKey && !progressGroups.some((item) => item.key === selectedGroupKey)) {
      setSelectedGroupKey(undefined);
      setSelectedOrderId(undefined);
    }
  }, [progressGroups, selectedGroupKey]);

  const activeGroup = useMemo(
    () => progressGroups.find((item) => item.key === selectedGroupKey) ?? null,
    [progressGroups, selectedGroupKey]
  );

  const activeOrder = useMemo(() => {
    if (!activeGroup) return null;
    return (
      activeGroup.orders.find((item) => item.orderId === selectedOrderId) ??
      activeGroup.orders.find((item) => item.orderId === activeGroup.currentOrderId) ??
      activeGroup.orders[0] ??
      null
    );
  }, [activeGroup, selectedOrderId]);

  const summary = useMemo(() => {
    const totalOrders = progressGroups.reduce((sum, item) => sum + item.totalCount, 0);
    const completedOrders = progressGroups.reduce((sum, item) => sum + item.completedCount, 0);
    const pendingOrders = progressGroups.reduce((sum, item) => sum + item.pendingCount, 0);
    const activeAreaCount = new Set(progressGroups.map((item) => item.areaCode)).size;
    const activeRiderCount = progressGroups.filter((item) => !item.missingRider).length;
    return { totalOrders, completedOrders, pendingOrders, activeAreaCount, activeRiderCount };
  }, [progressGroups]);

  return (
    <div className="admin-stack">
      <div className="dispatch-section">
        <div className="dispatch-section__header">
          <div>
            <div className="dispatch-section__title">骑手进度</div>
            <div className="dispatch-section__note">
              外层先看配送进度，点进区域和骑手后看队列，再点单查看备注、参考图和送达图，和骑手小程序保持同一浏览节奏。
            </div>
          </div>
          <span className="tag tag-blue">{summary.activeRiderCount} 位骑手</span>
        </div>

        <div style={{ display: "grid", gap: "16px" }}>
          <div className="dispatch-summary-grid">
            <div className="dispatch-stat-card">
              <div className="admin-panel-note" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <MapPinned size={16} />
                已覆盖区域
              </div>
              <div className="dispatch-stat-card__value">{summary.activeAreaCount}</div>
            </div>
            <div className="dispatch-stat-card">
              <div className="admin-panel-note" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <UserRound size={16} />
                在线进度卡
              </div>
              <div className="dispatch-stat-card__value">{progressGroups.length}</div>
            </div>
            <div className="dispatch-stat-card">
              <div className="admin-panel-note" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <PackageCheck size={16} />
                已送达
              </div>
              <div className="dispatch-stat-card__value is-success">{summary.completedOrders}</div>
            </div>
            <div className="dispatch-stat-card">
              <div className="admin-panel-note" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <Truck size={16} />
                待配送
              </div>
              <div className="dispatch-stat-card__value is-primary">{summary.pendingOrders}</div>
            </div>
          </div>

          <section className="admin-panel" style={{ padding: "20px", display: "grid", gap: "16px" }}>
            <div className="dispatch-section__title" style={{ marginBottom: "8px" }}>区域 / 骑手进度一览</div>
            {progressGroups.length === 0 ? (
              <div className="dispatch-empty" style={{ margin: 0, padding: "32px", background: "#fff", borderRadius: "12px" }}>当前没有可展示的骑手进度。</div>
            ) : (
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "12px" }}>
                {progressGroups.map((item) => {
                  const completionRatio = item.totalCount > 0 ? Math.round((item.completedCount / item.totalCount) * 100) : 0;
                  return (
                    <button
                      key={item.key}
                      type="button"
                      className="dispatch-card"
                      onClick={() => {
                        setSelectedGroupKey(item.key);
                        setSelectedOrderId(undefined);
                      }}
                      style={{ textAlign: "left" }}
                    >
                      <div className="dispatch-card__header">
                        <div>
                          <div className="dispatch-card__title">{item.areaCode || "未分区"}</div>
                          <div className="dispatch-card__subtitle">{item.riderName}</div>
                        </div>
                        {item.missingRider ? (
                          <span className="tag tag-red">缺骑手</span>
                        ) : item.exceptionCount > 0 ? (
                          <span className="tag tag-red">异常 {item.exceptionCount}</span>
                        ) : (
                          <span className="tag tag-green">正常</span>
                        )}
                      </div>
                      <div className="dispatch-chip-list">
                        <span className="tag tag-gray">总 {item.totalCount}</span>
                        <span className="tag tag-green">送 {item.completedCount}</span>
                        <span className="tag tag-blue">待 {item.pendingCount}</span>
                      </div>
                      <div style={{ display: "grid", gap: "6px" }}>
                        <div style={{ display: "flex", justifyContent: "space-between", fontSize: "12px", color: "var(--text-light)" }}>
                          <span>进度 {completionRatio}%</span>
                          <span>{item.currentSequenceNumber ? `当前 #${item.currentSequenceNumber}` : "等待开始"}</span>
                        </div>
                        <div style={{ height: "6px", borderRadius: "999px", background: "var(--border-light)" }}>
                          <div
                            style={{
                              width: `${completionRatio}%`,
                              height: "100%",
                              borderRadius: "999px",
                              background: "linear-gradient(90deg, var(--primary-color), #53b5ff)"
                            }}
                          />
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </section>
        </div>
      </div>

      <AdminDialog
        open={Boolean(activeGroup)}
        title={activeGroup ? `${activeGroup.areaCode} / ${activeGroup.riderName}` : "骑手队列"}
        description={activeGroup ? `共 ${activeGroup.totalCount} 单，已送 ${activeGroup.completedCount} 单，待送 ${activeGroup.pendingCount} 单` : undefined}
        width={1040}
        onClose={() => {
          setSelectedGroupKey(undefined);
          setSelectedOrderId(undefined);
        }}
      >
        {activeGroup ? (
          <div style={{ display: "grid", gap: "16px" }}>
            <div className="dispatch-dialog-header">
              <div>
                <div className="dispatch-card__title">{activeGroup.areaCode}</div>
                <div className="dispatch-inline-note">骑手：{activeGroup.riderName}</div>
              </div>
              <div className="dispatch-chip-list">
                <span className="tag tag-gray">共 {activeGroup.totalCount} 单</span>
                <span className="tag tag-green">已送 {activeGroup.completedCount}</span>
                <span className="tag tag-blue">待送 {activeGroup.pendingCount}</span>
              </div>
            </div>

            {activeGroup.orders.length === 0 ? (
              <div className="dispatch-empty" style={{ margin: 0 }}>当前区域暂无可查看订单。</div>
            ) : (
              <div className="dispatch-dialog-grid">
                {activeGroup.orders.map((order) => {
                  const isCurrent = order.orderId === activeGroup.currentOrderId;
                  const stateClass = isCurrent
                    ? "is-current"
                    : order.deliveryStatus === "DELIVERED"
                      ? "is-delivered"
                      : "is-pending";
                  return (
                    <button
                      key={order.orderId}
                      type="button"
                      className={`dispatch-order-tile ${stateClass}`}
                      onClick={() => setSelectedOrderId(order.orderId)}
                    >
                      <div className="dispatch-order-tile__top">
                        <div>
                          <strong style={{ fontSize: "15px", display: "block", marginBottom: "4px" }}>
                            #{order.sequenceNumber} {order.customerName}
                          </strong>
                          <div className="dispatch-inline-note">{order.deliveryAddress}</div>
                        </div>
                        <span className={`tag ${isCurrent ? "tag-blue" : order.deliveryStatus === "DELIVERED" ? "tag-green" : "tag-amber"}`}>
                          {dispatchOrderStatusLabel(order.deliveryStatus, isCurrent)}
                        </span>
                      </div>
                      <div className="dispatch-chip-list">
                        {hasOrderAttention(order) && <span className="tag tag-amber">需留意</span>}
                        {hasDisplayValue(order.userNote) && <span className="tag tag-gray">用户备注</span>}
                        {hasDisplayValue(order.merchantRemark) && <span className="tag tag-gray">商家备注</span>}
                        {hasDisplayValue(order.referenceImageUrl) && <span className="tag tag-gray">参照图</span>}
                        {hasDisplayValue(order.receiptUrl) && <span className="tag tag-gray">送达图</span>}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        ) : null}
      </AdminDialog>

      <AdminDialog
        open={Boolean(activeGroup && activeOrder && selectedOrderId !== undefined)}
        title={activeOrder ? `订单详情 - #${activeOrder.sequenceNumber} ${activeOrder.customerName}` : "订单详情"}
        width={920}
        zOffset={10}
        onClose={() => setSelectedOrderId(undefined)}
      >
        {activeOrder ? (
          <div style={{ display: "grid", gap: "16px" }}>
            <div className="dispatch-dialog-header">
              <div>
                <div className="dispatch-card__title">{activeGroup?.areaCode || "-"}</div>
                <div className="dispatch-inline-note">骑手：{activeGroup?.riderName || "未分配骑手"}</div>
              </div>
              <div className="dispatch-chip-list">
                <span className={`tag ${activeOrder.deliveryStatus === "DELIVERED" ? "tag-green" : "tag-blue"}`}>
                  {dispatchOrderStatusLabel(activeOrder.deliveryStatus, activeOrder.orderId === activeGroup?.currentOrderId)}
                </span>
                {activeOrder.quantity > 1 ? <span className="tag tag-gray">数量 ×{activeOrder.quantity}</span> : null}
              </div>
            </div>

            <div className="dispatch-detail-grid">
              <section className="dispatch-detail-panel">
                <div className="dispatch-section__title" style={{ marginBottom: 0, fontSize: "15px" }}>订单信息</div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">客户姓名</div>
                  <div>{activeOrder.customerName || "-"}</div>
                </div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">配送地址</div>
                  <div>{activeOrder.deliveryAddress || "-"}</div>
                </div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">用户备注</div>
                  <div>{hasDisplayValue(activeOrder.userNote) ? activeOrder.userNote : "-"}</div>
                </div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">商家备注</div>
                  <div>{hasDisplayValue(activeOrder.merchantRemark) ? activeOrder.merchantRemark : "-"}</div>
                </div>
              </section>

              <section className="dispatch-detail-panel">
                <div className="dispatch-section__title" style={{ marginBottom: 0, fontSize: "15px" }}>送达信息</div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">回单说明</div>
                  <div>{hasDisplayValue(activeOrder.receiptNote) ? activeOrder.receiptNote : "-"}</div>
                </div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">送达时间</div>
                  <div>{activeOrder.deliveredAt || "-"}</div>
                </div>
              </section>
            </div>

            <div className="dispatch-image-grid">
              <section className="dispatch-detail-panel">
                <div className="admin-panel-note">地址参照图</div>
                {hasDisplayValue(activeOrder.referenceImageUrl) ? (
                  <img
                    src={activeOrder.referenceImageUrl}
                    alt="地址参照图"
                    style={{ width: "100%", borderRadius: "12px", border: "1px solid var(--border-color)", objectFit: "cover", aspectRatio: "3 / 4" }}
                  />
                ) : (
                  <div className="dispatch-image-empty">暂无参照图</div>
                )}
              </section>

              <section className="dispatch-detail-panel">
                <div className="admin-panel-note">本次送达图</div>
                {hasDisplayValue(activeOrder.receiptUrl) ? (
                  <img
                    src={activeOrder.receiptUrl}
                    alt="本次送达图"
                    style={{ width: "100%", borderRadius: "12px", border: "1px solid var(--border-color)", objectFit: "cover", aspectRatio: "3 / 4" }}
                  />
                ) : (
                  <div className="dispatch-image-empty">暂无送达图</div>
                )}
              </section>
            </div>
          </div>
        ) : null}
      </AdminDialog>
    </div>
  );
}
