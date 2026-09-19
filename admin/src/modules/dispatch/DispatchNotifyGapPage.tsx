import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Inbox, RefreshCw, Sun, Moon } from "lucide-react";
import { fetchDeliveryNotifyGaps, extractAdminApiErrorMessage } from "../../shared/api/http";
import type { DeliveryNotifyGapItem } from "../../shared/api/types";
import { toast } from "../../shared/components/Toast";
import { useDispatchContext } from "./DispatchContext";
import { mealPeriodLabel } from "./dispatchCenterLayout.helpers";

/** 未送达原因 → 运营可读的标签与处理提示（后端 DeliveryNotifyGapItem.reason） */
const REASON_META: Record<string, { label: string; tagClass: string; hint: string }> = {
  NO_SUBSCRIPTION: {
    label: "未授权取餐提醒",
    tagClass: "tag tag-amber",
    hint: "该订单没有取餐提醒授权记录（常见于后台代客下单），系统无法下发，需人工告知客户"
  },
  REVOKED: {
    label: "客户已关闭订阅",
    tagClass: "tag tag-red",
    hint: "客户在微信里关闭了订阅授权，系统不会再自动发送，需人工告知客户"
  },
  RETRY_EXHAUSTED: {
    label: "下发失败已放弃",
    tagClass: "tag tag-red",
    hint: "微信下发多次失败已停止重试，需人工告知客户"
  }
};

function reasonMeta(reason: string) {
  return (
    REASON_META[reason] || {
      label: reason || "未知原因",
      tagClass: "tag tag-gray",
      hint: "系统无法自动送达取餐提醒，需人工告知客户"
    }
  );
}

/**
 * 取餐提醒未送达名单：骑手已送达、但取餐提醒确定发不出去、需要人工补漏的客户。
 *
 * <p>只展示三类终态（未授权 / 客户关闭订阅 / 下发失败已放弃）；仍在重试、以及尚未到餐段释放时间的
 * 订单不会出现，避免把正常订单误判为漏发。按顶部餐段切换区分午餐与晚餐。
 */
export function DispatchNotifyGapPage() {
  const { serveDate, mealPeriod } = useDispatchContext();
  const [items, setItems] = useState<DeliveryNotifyGapItem[]>([]);
  const [loading, setLoading] = useState(false);

  const loadGaps = useCallback(async () => {
    setLoading(true);
    try {
      // 一次取回当日全部餐段，切换顶部午餐/晚餐时无需重新请求
      const result = await fetchDeliveryNotifyGaps({ serveDate });
      setItems(result || []);
    } catch (error) {
      toast(extractAdminApiErrorMessage(error, "加载未通知名单失败"), "error");
    } finally {
      setLoading(false);
    }
  }, [serveDate]);

  useEffect(() => {
    loadGaps().catch(() => undefined);
  }, [loadGaps]);

  const periodItems = useMemo(
    () => items.filter((item) => item.mealPeriod === mealPeriod),
    [items, mealPeriod]
  );
  // 另一餐段的人数：提示运营「晚餐还有 N 人未收到」，避免只看当前餐段漏掉另一半
  const otherPeriodCount = useMemo(
    () => items.filter((item) => item.mealPeriod !== mealPeriod).length,
    [items, mealPeriod]
  );

  const currentPeriodColor = mealPeriod === "DINNER" ? "#6366f1" : "#f59e0b";
  const currentPeriodIcon = mealPeriod === "DINNER"
    ? <Moon size={16} color={currentPeriodColor} />
    : <Sun size={16} color={currentPeriodColor} />;

  function renderGapCard(item: DeliveryNotifyGapItem) {
    const meta = reasonMeta(item.reason);
    return (
      <div
        key={item.orderId}
        className="dispatch-area-orders__item"
        style={{
          border: "1px solid var(--border-color)",
          borderRadius: "12px",
          padding: "14px 16px",
          background: "var(--bg-card)"
        }}
      >
        <div className="dispatch-area-orders__top">
          <strong>订单 #{item.orderId}</strong>
          <span className={meta.tagClass}>{meta.label}</span>
        </div>
        <div style={{ marginTop: 6 }}>
          {item.customerName}（{item.customerPhone || "--"}）
        </div>
        <div className="dispatch-order-item__meta">{item.deliveryAddress}</div>
        <div className="dispatch-order-item__meta" style={{ marginTop: 4 }}>
          送达时间 {item.deliveredAt ? item.deliveredAt.replace("T", " ") : "--"}
          {item.quantity > 1 ? ` · ${item.quantity} 份` : ""}
        </div>
        <div className="dispatch-inline-note" style={{ marginTop: 6 }}>{meta.hint}</div>
      </div>
    );
  }

  return (
    <div className="admin-stack">
      <div className="toolbar">
        <div className="dispatch-toolbar">
          <div>
            <div className="dispatch-section__title">取餐提醒未送达名单</div>
            <div className="dispatch-section__note">
              骑手已送达、但取餐提醒确定发不出去（客户未授权 / 已关闭订阅 / 下发失败）的订单会显示在这里，
              需要人工电话或微信告知客户。仍在重试、以及还没到释放时间的订单不会出现。
            </div>
          </div>
          <div className="dispatch-toolbar__actions">
            <button className="btn btn-outline" disabled={loading} onClick={() => { loadGaps().catch(() => undefined); }}>
              <RefreshCw size={16} /> 刷新
            </button>
          </div>
        </div>
      </div>

      <div className="dispatch-summary-grid" style={{ gridTemplateColumns: "1fr" }}>
        <div className="dispatch-stat-card" style={{ borderLeft: `4px solid ${currentPeriodColor}` }}>
          <div className="admin-panel-note" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            {currentPeriodIcon} {mealPeriodLabel(mealPeriod)}未收到提醒
          </div>
          <div className="dispatch-stat-card__value" style={{ fontSize: "24px", fontWeight: 800 }}>{periodItems.length}</div>
          <div className="dispatch-stat-card__footer">
            {mealPeriodLabel(mealPeriod)}已送达但提醒未送达的客户数
            {otherPeriodCount > 0 ? `（另一餐段还有 ${otherPeriodCount} 人，切换顶部餐段查看）` : ""}
          </div>
        </div>
      </div>

      {loading && items.length === 0 ? (
        <div className="admin-empty-note">加载中...</div>
      ) : periodItems.length === 0 ? (
        <div
          className="admin-empty-note"
          style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "8px", padding: "48px 0" }}
        >
          <Inbox size={32} />
          <div>当前没有未收到提醒的{mealPeriodLabel(mealPeriod)}客户</div>
          <div className="dispatch-inline-note">已送达订单的取餐提醒都已正常下发。</div>
        </div>
      ) : (
        <section
          className="admin-stack"
          style={{ gap: "12px", border: "1px solid var(--border-color)", borderRadius: "16px", padding: "16px", background: "var(--bg-page)" }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "8px", fontWeight: 700 }}>
            {currentPeriodIcon}
            <span>{mealPeriodLabel(mealPeriod)}时段</span>
            <span className="tag tag-gray" style={{ marginLeft: "auto" }}>{periodItems.length} 人未收到</span>
          </div>
          {periodItems.map((item) => renderGapCard(item))}
        </section>
      )}
    </div>
  );
}
