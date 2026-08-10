import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Send, RefreshCw, Inbox, Sun, Moon } from "lucide-react";
import { fetchDeliveryReleasePending, releaseDeliveredOrder, fetchOperationSettings } from "../../shared/api/http";
import type { DeliveryReleasePendingItem } from "../../shared/api/types";
import { toast } from "../../shared/components/Toast";
import { useDispatchContext } from "./DispatchContext";
import { mealPeriodLabel } from "./dispatchCenterLayout.helpers";

function getErrorMessage(error: unknown, fallback = "操作失败") {
  if (typeof error === "object" && error !== null) {
    const errorLike = error as { response?: { data?: { message?: string } }; message?: string };
    return errorLike.response?.data?.message || errorLike.message || fallback;
  }
  return typeof error === "string" ? error : fallback;
}

function subscriptionLabel(status: string) {
  if (status === "SENT") {
    return "已发送";
  }
  if (status === "AUTHORIZED" || status === "FAILED") {
    return "待发送";
  }
  return "未订阅";
}

/**
 * 待释放送达：骑手已送达、但尚未到餐期释放时间（系统设置中配置的午餐/晚餐释放时间）的订单。
 * 释放前用户端仍显示"待配送"、看不到回执；商家可在此对个别订单"立即释放"，
 * 释放后该订单对用户立即变为"已送达"、回执可见，并发送取餐提醒订阅消息。
 */
export function DispatchReleasePage() {
  const { serveDate, mealPeriod } = useDispatchContext();
  const [items, setItems] = useState<DeliveryReleasePendingItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [releasingId, setReleasingId] = useState<number | null>(null);
  const [lunchReleaseTime, setLunchReleaseTime] = useState("11:30");
  const [dinnerReleaseTime, setDinnerReleaseTime] = useState("17:00");

  const loadPending = useCallback(async () => {
    setLoading(true);
    try {
      const result = await fetchDeliveryReleasePending({ serveDate, mealPeriod });
      setItems(result || []);
    } catch (error: any) {
      toast(getErrorMessage(error, "加载待释放订单失败"), "error");
    } finally {
      setLoading(false);
    }
  }, [serveDate, mealPeriod]);

  const loadReleaseTimes = useCallback(async () => {
    try {
      const settings = await fetchOperationSettings();
      if (settings?.deliverySubscribeLunchTime) {
        setLunchReleaseTime(settings.deliverySubscribeLunchTime);
      }
      if (settings?.deliverySubscribeDinnerTime) {
        setDinnerReleaseTime(settings.deliverySubscribeDinnerTime);
      }
    } catch {
      // 读取配置失败时保留默认展示时间
    }
  }, []);

  useEffect(() => {
    loadPending().catch(() => undefined);
    loadReleaseTimes().catch(() => undefined);
  }, [loadPending, loadReleaseTimes]);

  async function handleRelease(item: DeliveryReleasePendingItem) {
    if (releasingId !== null) {
      return;
    }
    setReleasingId(item.orderId);
    try {
      const result = await releaseDeliveredOrder(item.orderId);
      if (result.subscriptionSent) {
        toast(`订单 #${item.orderId} 已释放并发送取餐提醒给用户`);
      } else {
        toast(`订单 #${item.orderId} 已释放（该用户未订阅提醒，仅状态与图片对用户可见）`);
      }
      await loadPending();
    } catch (error: any) {
      toast(getErrorMessage(error, "释放失败"), "error");
    } finally {
      setReleasingId(null);
    }
  }

  const currentReleaseTime = mealPeriod === "DINNER" ? dinnerReleaseTime : lunchReleaseTime;
  const currentPeriodColor = mealPeriod === "DINNER" ? "#6366f1" : "#f59e0b";
  const currentPeriodIcon = mealPeriod === "DINNER" ? <Moon size={16} color={currentPeriodColor} /> : <Sun size={16} color={currentPeriodColor} />;

  const filteredItems = useMemo(() => items.filter((item) => item.mealPeriod === mealPeriod), [items, mealPeriod]);

  function renderOrderCard(item: DeliveryReleasePendingItem) {
    return (
      <div
        key={item.orderId}
        className="dispatch-area-orders__item"
        style={{ border: "1px solid var(--border-color)", borderRadius: "12px", padding: "14px 16px", background: "var(--bg-card)" }}
      >
        <div className="dispatch-area-orders__top">
          <strong>订单 #{item.orderId}</strong>
          <span className="tag tag-amber">
            {item.serveDate}
            {item.quantity > 1 ? ` ×${item.quantity}` : ""}
          </span>
        </div>
        <div style={{ marginTop: 6 }}>
          {item.customerName}（{item.customerPhone || "--"}）
        </div>
        <div className="dispatch-order-item__meta">{item.deliveryAddress}</div>
        <div className="dispatch-order-item__meta" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "8px", marginTop: 4 }}>
          <span>
            送达时间 {item.deliveredAt ? item.deliveredAt.replace("T", " ") : "--"}
            <span style={{ marginLeft: 12 }}>订阅：{subscriptionLabel(item.subscriptionStatus)}</span>
          </span>
          <button
            className="btn btn-primary btn-compact"
            disabled={releasingId !== null}
            onClick={() => handleRelease(item).catch(() => undefined)}
          >
            <Send size={14} />
            {releasingId === item.orderId ? "释放中..." : "立即释放"}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-stack">
      <div className="toolbar">
        <div className="dispatch-toolbar">
          <div>
            <div className="dispatch-section__title">待释放送达</div>
            <div className="dispatch-section__note">
              骑手已送达、但未到释放时间的订单会显示在这里。释放时间取自「系统设置 - 餐包提醒」，可修改。
              到点后订单会自动对用户可见并发送提醒；遇特殊情况可对个别订单"立即释放"。
            </div>
          </div>
          <div className="dispatch-toolbar__actions">
            <button className="btn btn-outline" disabled={loading} onClick={() => { loadPending().catch(() => undefined); loadReleaseTimes().catch(() => undefined); }}>
              <RefreshCw size={16} /> 刷新
            </button>
          </div>
        </div>
      </div>

      <div className="dispatch-summary-grid" style={{ gridTemplateColumns: "1fr" }}>
        <div className="dispatch-stat-card" style={{ borderLeft: `4px solid ${currentPeriodColor}` }}>
          <div className="admin-panel-note" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            {currentPeriodIcon} {mealPeriodLabel(mealPeriod)}释放时间
          </div>
          <div className="dispatch-stat-card__value" style={{ fontSize: "24px", fontWeight: 800 }}>{currentReleaseTime}</div>
          <div className="dispatch-stat-card__footer">{mealPeriodLabel(mealPeriod)}已送达订单在该时间点统一释放</div>
        </div>
      </div>

      {loading && items.length === 0 ? (
        <div className="admin-empty-note">加载中...</div>
      ) : filteredItems.length === 0 ? (
        <div className="admin-empty-note" style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "8px", padding: "48px 0" }}>
          <Inbox size={32} />
          <div>当前没有待释放的{mealPeriodLabel(mealPeriod)}订单</div>
          <div className="dispatch-inline-note">到释放时间后，订单会自动对用户可见并发送提醒，无需手动处理。</div>
        </div>
      ) : (
        <div className="admin-stack" style={{ gap: "16px" }}>
          <section
            className="admin-stack"
            style={{ gap: "12px", border: "1px solid var(--border-color)", borderRadius: "16px", padding: "16px", background: "var(--bg-page)" }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "8px", fontWeight: 700 }}>
              {currentPeriodIcon}
              <span>{mealPeriodLabel(mealPeriod)}时段</span>
              <span className="tag tag-gray" style={{ marginLeft: "auto" }}>{filteredItems.length} 单待释放</span>
            </div>
            {filteredItems.map((item) => renderOrderCard(item))}
          </section>
        </div>
      )}
    </div>
  );
}
