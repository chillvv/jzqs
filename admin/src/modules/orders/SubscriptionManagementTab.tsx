import React, { useEffect, useMemo, useState } from "react";
import { fetchSubscriptionRules, deleteSubscriptionRule, toggleSubscriptionRule } from "../../shared/api/http";
import type { SubscriptionRuleResponse } from "../../shared/api/types";
import { SubscriptionRuleForm } from "./SubscriptionRuleForm";
import { AlertTriangle, Edit, Pause, Play, Trash2 } from "lucide-react";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { AsyncContentView, type AsyncContentViewStatus } from "../../shared/components/AsyncContentView";
import { toast } from "../../shared/components/Toast";
import { formatDateLabel } from "../../shared/utils/dateTime";
import { buildCrossMealDeliveryRemark, formatOrderNote } from "./orderPrepPage.helpers";

export type SubscriptionStatusFilter = "ALL" | "ACTIVE" | "STOPPED" | "EXPIRED";
export type SubscriptionMealPeriod = "LUNCH" | "DINNER";
export type SubscriptionManagementFilters = {
  keyword: string;
  statusFilter: SubscriptionStatusFilter;
  mealPeriod: SubscriptionMealPeriod;
};

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

type SubscriptionManagementTabProps = {
  filters: SubscriptionManagementFilters;
  queryVersion: number;
  onVisibleCountChange?: (count: number) => void;
};

export function SubscriptionManagementTab({ filters, queryVersion, onVisibleCountChange }: SubscriptionManagementTabProps) {
  const [items, setItems] = useState<SubscriptionRuleResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<SubscriptionRuleResponse | null>(null);
  const [togglingRuleId, setTogglingRuleId] = useState<number | null>(null);
  const [deletingRuleId, setDeletingRuleId] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<SubscriptionRuleResponse | null>(null);

  useEffect(() => {
    loadData().catch(() => undefined);
  }, [queryVersion]);

  async function loadData() {
    setLoading(true);
    setError(null);
    try {
      const requestedStatus = filters.statusFilter === "ALL" || filters.statusFilter === "STOPPED" ? undefined : filters.statusFilter;
      const data = await fetchSubscriptionRules(filters.keyword || undefined, requestedStatus);
      setItems(
        filters.statusFilter === "STOPPED"
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
    loadData().catch(() => undefined);
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

  const visibleItems = useMemo(
    () => items.filter((item) => (
      filters.mealPeriod === "LUNCH" ? item.lunchEnabled : item.dinnerEnabled
    )),
    [items, filters.mealPeriod]
  );

  useEffect(() => {
    onVisibleCountChange?.(visibleItems.length);
  }, [visibleItems.length, onVisibleCountChange]);

  const currentMealLabel = filters.mealPeriod === "DINNER" ? "晚餐" : "午餐";

  const subscriptionListStatus: AsyncContentViewStatus = loading
    ? "loading"
    : error
      ? "error"
      : visibleItems.length === 0
        ? "empty"
        : "success";

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: "16px" }}>
        <button className="btn btn-primary" onClick={handleCreate} style={{ marginLeft: "auto" }}>
          + 新增计划
        </button>
      </div>

      {subscriptionListStatus !== "success" ? (
        <AsyncContentView
          status={subscriptionListStatus}
          error={error ?? undefined}
          emptyText={`暂无${currentMealLabel}固定订餐计划`}
        />
      ) : (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>客户</th>
                <th>电话</th>
                <th>生效周期</th>
                <th>商家备注</th>
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
                const deliveryMealPeriod = filters.mealPeriod === "LUNCH"
                  ? item.lunchDeliveryMealPeriod
                  : item.dinnerDeliveryMealPeriod;
                const merchantRemark = formatOrderNote(
                  buildCrossMealDeliveryRemark(item.merchantRemark, filters.mealPeriod, deliveryMealPeriod)
                );
                return (
                  <tr key={item.id}>
                    <td>
                      <div style={{ display: "flex", alignItems: "center" }}>
                        <span>{item.customerName}</span>
                        <span style={{ color: "var(--primary-color)", marginLeft: "6px", fontWeight: 700 }}>
                          ×{filters.mealPeriod === "LUNCH" ? item.lunchQuantity : item.dinnerQuantity}
                        </span>
                      </div>
                    </td>
                    <td>{item.customerPhone}</td>
                    <td>
                      {formatSubscriptionDateRange(item.startDate, item.endDate)}
                    </td>
                    <td style={{ maxWidth: "220px" }}>{merchantRemark}</td>
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
