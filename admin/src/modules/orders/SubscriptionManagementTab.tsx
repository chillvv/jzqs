import React, { useEffect, useState } from "react";
import { fetchSubscriptionRules, deleteSubscriptionRule, toggleSubscriptionRule } from "../../shared/api/http";
import type { SubscriptionRuleResponse } from "../../shared/api/types";
import { SubscriptionRuleForm } from "./SubscriptionRuleForm";
import { AlertTriangle, Edit, Pause, Play, Trash2 } from "lucide-react";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { toast } from "../../shared/components/Toast";
import { formatDateLabel } from "../../shared/utils/dateTime";

type SubscriptionStatusFilter = "ALL" | "ACTIVE" | "STOPPED" | "EXPIRED";
type SubscriptionMealPeriod = "LUNCH" | "DINNER";
const SUBSCRIPTION_MEAL_PERIOD_STORAGE_KEY = "admin-subscription-management-meal-period";

function resolveStoredSubscriptionMealPeriod(): SubscriptionMealPeriod {
  if (typeof window === "undefined") {
    return "LUNCH";
  }
  return window.localStorage.getItem(SUBSCRIPTION_MEAL_PERIOD_STORAGE_KEY) === "DINNER" ? "DINNER" : "LUNCH";
}

function formatSubscriptionDateRange(startDate?: string, endDate?: string) {
  const startLabel = formatDateLabel(startDate);
  const endLabel = formatDateLabel(endDate);
  if (startLabel === "-" && endLabel === "-") {
    return "-";
  }
  if (startLabel === endLabel) {
    return startLabel;
  }
  return `${startLabel} - ${endLabel}`;
}

export function SubscriptionManagementTab() {
  const [items, setItems] = useState<SubscriptionRuleResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<SubscriptionStatusFilter>("ALL");
  const [mealPeriod, setMealPeriod] = useState<SubscriptionMealPeriod>(() => resolveStoredSubscriptionMealPeriod());
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<SubscriptionRuleResponse | null>(null);
  const [togglingRuleId, setTogglingRuleId] = useState<number | null>(null);
  const [deletingRuleId, setDeletingRuleId] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<SubscriptionRuleResponse | null>(null);

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    if (typeof window !== "undefined") {
      window.localStorage.setItem(SUBSCRIPTION_MEAL_PERIOD_STORAGE_KEY, mealPeriod);
    }
  }, [mealPeriod]);

  async function loadData() {
    setLoading(true);
    setError(null);
    try {
      const requestedStatus = statusFilter === "ALL" || statusFilter === "STOPPED" ? undefined : statusFilter;
      const data = await fetchSubscriptionRules(keyword || undefined, requestedStatus);
      setItems(
        statusFilter === "STOPPED"
          ? data.filter((item) => item.status === "PAUSED" || item.status === "INACTIVE" || item.paused)
          : data
      );
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || "加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(id: number) {
    if (deletingRuleId === id) {
      return;
    }
    setDeletingRuleId(id);
    try {
      await deleteSubscriptionRule(id);
      setDeleteTarget(null);
      await loadData();
      toast("固定订餐计划已删除");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "删除失败", "error");
    } finally {
      setDeletingRuleId(null);
    }
  }

  async function handleToggle(id: number) {
    if (togglingRuleId === id) {
      return;
    }
    setTogglingRuleId(id);
    try {
      await toggleSubscriptionRule(id);
      await loadData();
      toast("固定订餐计划状态已更新");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "操作失败", "error");
    } finally {
      setTogglingRuleId(null);
    }
  }

  function handleEdit(item: SubscriptionRuleResponse) {
    setEditingItem(item);
    setIsFormOpen(true);
  }

  function handleCreate() {
    setEditingItem(null);
    setIsFormOpen(true);
  }

  function handleFormClose() {
    setIsFormOpen(false);
    setEditingItem(null);
    loadData();
  }

  const getStatusMeta = (item: Pick<SubscriptionRuleResponse, "status" | "paused">) => {
    if (item.status === "PAUSED" || item.status === "INACTIVE" || item.paused) {
      return { label: "已停用", className: "subscription-status-pill subscription-status-pill--stopped" };
    }
    switch (item.status) {
      case "ACTIVE":
        return { label: "进行中", className: "subscription-status-pill subscription-status-pill--active" };
      case "EXPIRED":
        return { label: "已过期", className: "subscription-status-pill subscription-status-pill--expired" };
      default:
        return { label: item.status, className: "subscription-status-pill subscription-status-pill--default" };
    }
  };

  const renderMealCell = (
    enabled: boolean,
    quantity: number,
    deliveryMealPeriod: string,
    tone: "lunch" | "dinner"
  ) => {
    if (!enabled) {
      return <span style={{ color: "var(--text-sub)" }}>-</span>;
    }
    return (
      <div className={`subscription-rule-meal subscription-rule-meal--${tone}`}>
        <div className="subscription-rule-meal__meta">
          <span>{quantity} 份 / 天</span>
          <span>配送餐次：{deliveryMealPeriod === "DINNER" ? "晚餐" : "午餐"}</span>
        </div>
      </div>
    );
  };

  const visibleItems = items.filter((item) => (
    mealPeriod === "LUNCH" ? item.lunchEnabled : item.dinnerEnabled
  ));

  const currentMealLabel = mealPeriod === "DINNER" ? "晚餐" : "午餐";

  return (
    <div>
      <div style={{ display: "flex", gap: "12px", marginBottom: "16px", flexWrap: "wrap" }}>
        <div className="subscription-meal-toggle">
          <div className="segmented-control" role="tablist" aria-label="固定订餐餐次切换">
            {(["LUNCH", "DINNER"] as SubscriptionMealPeriod[]).map((value) => (
              <button
                key={value}
                type="button"
                className={`segmented-control__item ${mealPeriod === value ? "is-active" : ""}`}
                onClick={() => setMealPeriod(value)}
              >
                {value === "DINNER" ? "晚餐" : "午餐"}
              </button>
            ))}
          </div>
        </div>
        <input
          type="text"
          className="input-box"
          placeholder="搜索客户姓名或电话"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          style={{ width: "200px" }}
        />
        <select
          className="input-box"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as SubscriptionStatusFilter)}
          style={{ width: "120px" }}
        >
          <option value="ALL">全部状态</option>
          <option value="ACTIVE">进行中</option>
          <option value="STOPPED">已停用</option>
          <option value="EXPIRED">已过期</option>
        </select>
        <button className="btn btn-primary" onClick={loadData} disabled={loading}>
          {loading ? "查询中..." : "查询"}
        </button>
        <button className="btn btn-primary" onClick={handleCreate} style={{ marginLeft: "auto" }}>
          + 新增计划
        </button>
      </div>

      {loading && (
        <div className="dispatch-empty">加载中...</div>
      )}

      {error && (
        <div className="dispatch-empty" style={{ color: "var(--error-color)" }}>
          {error}
        </div>
      )}

      {!loading && !error && visibleItems.length === 0 && (
        <div className="dispatch-empty">暂无{currentMealLabel}固定订餐计划</div>
      )}

      {!loading && !error && visibleItems.length > 0 && (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>客户</th>
                <th>电话</th>
                <th>生效周期</th>
                <th>{currentMealLabel}</th>
                <th>剩余餐数</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {visibleItems.map((item) => {
                const statusInfo = getStatusMeta(item);
                const isLowBalance = item.remainingMeals <= 3;
                const canToggle = item.status === "ACTIVE" || item.status === "PAUSED";
                const isPaused = item.status === "PAUSED" || item.paused;
                return (
                  <tr key={item.id}>
                    <td>{item.customerName}</td>
                    <td>{item.customerPhone}</td>
                    <td>
                      {formatSubscriptionDateRange(item.startDate, item.endDate)}
                    </td>
                    <td>
                      {mealPeriod === "LUNCH"
                        ? renderMealCell(item.lunchEnabled, item.lunchQuantity, item.lunchDeliveryMealPeriod, "lunch")
                        : renderMealCell(item.dinnerEnabled, item.dinnerQuantity, item.dinnerDeliveryMealPeriod, "dinner")}
                    </td>
                    <td>
                      <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                        <span
                          style={{
                            color: isLowBalance ? "var(--error-color)" : "inherit",
                            fontWeight: isLowBalance ? 600 : 400
                          }}
                        >
                          {item.remainingMeals} 餐
                        </span>
                        {isLowBalance && <AlertTriangle size={14} color="var(--error-color)" />}
                      </div>
                    </td>
                    <td>
                      <span className={statusInfo.className}>{statusInfo.label}</span>
                    </td>
                    <td>
                      <div className="subscription-rule-actions">
                        <button
                          className="btn btn-sm btn-outline subscription-rule-actions__button"
                          onClick={() => handleEdit(item)}
                          title="编辑"
                        >
                          <Edit size={14} />
                          <span>编辑</span>
                        </button>
                        {canToggle && (
                          <button
                            className="btn btn-sm btn-outline subscription-rule-actions__button"
                            onClick={() => handleToggle(item.id)}
                            disabled={togglingRuleId === item.id}
                            title={isPaused ? "重新启用" : "暂停计划"}
                          >
                            {togglingRuleId === item.id ? "处理中..." : (
                              <>
                                {isPaused ? <Play size={14} /> : <Pause size={14} />}
                                <span style={{ marginLeft: "4px" }}>{isPaused ? "重新启用" : "暂停"}</span>
                              </>
                            )}
                          </button>
                        )}
                        <button
                          className="btn btn-sm btn-outline subscription-rule-actions__button subscription-rule-actions__button--danger"
                          onClick={() => setDeleteTarget(item)}
                          disabled={deletingRuleId === item.id}
                          title="删除"
                        >
                          {deletingRuleId === item.id ? "删除中..." : <Trash2 size={14} />}
                          {deletingRuleId === item.id ? null : <span>删除</span>}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {isFormOpen && (
        <SubscriptionRuleForm
          item={editingItem}
          onClose={handleFormClose}
        />
      )}

      <AdminDialog
        open={Boolean(deleteTarget)}
        title="确认删除固定订餐计划"
        description={deleteTarget ? `确认删除 ${deleteTarget.customerName} 的固定订餐计划吗？` : undefined}
        width={480}
        onClose={deletingRuleId ? () => undefined : () => setDeleteTarget(null)}
        footer={
          <>
            <button className="btn btn-outline" disabled={Boolean(deletingRuleId)} onClick={() => setDeleteTarget(null)}>取消</button>
            <button
              className="btn-delete"
              disabled={!deleteTarget || Boolean(deletingRuleId)}
              onClick={() => deleteTarget && handleDelete(deleteTarget.id).catch((err) => toast(err?.response?.data?.message || err?.message || "删除失败", "error"))}
            >
              {deletingRuleId ? "删除中..." : "确认删除"}
            </button>
          </>
        }
      >
        {deleteTarget ? (
          <div className="delete-confirm-details">
            <div className="delete-confirm-details__item">
              <span className="delete-confirm-details__label">客户：</span>
              <span className="delete-confirm-details__value">{deleteTarget.customerName}</span>
            </div>
            <div className="delete-confirm-details__item">
              <span className="delete-confirm-details__label">电话：</span>
              <span className="delete-confirm-details__value">{deleteTarget.customerPhone}</span>
            </div>
            <div className="delete-confirm-details__item">
              <span className="delete-confirm-details__label">状态：</span>
              <span className="delete-confirm-details__value">{getStatusMeta(deleteTarget).label}</span>
            </div>
          </div>
        ) : null}
      </AdminDialog>
    </div>
  );
}
