import React, { useEffect, useState } from "react";
import { fetchSubscriptionRules, deleteSubscriptionRule, toggleSubscriptionRule } from "../../shared/api/http";
import type { SubscriptionRuleResponse } from "../../shared/api/types";
import { SubscriptionRuleForm } from "./SubscriptionRuleForm";
import { AlertTriangle, Edit, Pause, Play, Trash2 } from "lucide-react";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { toast } from "../../shared/components/Toast";

export function SubscriptionManagementTab() {
  const [items, setItems] = useState<SubscriptionRuleResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<SubscriptionRuleResponse | null>(null);
  const [togglingRuleId, setTogglingRuleId] = useState<number | null>(null);
  const [deletingRuleId, setDeletingRuleId] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<SubscriptionRuleResponse | null>(null);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchSubscriptionRules(keyword || undefined, statusFilter === "ALL" ? undefined : statusFilter);
      setItems(data);
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

  const getStatusLabel = (status: string) => {
    switch (status) {
      case "ACTIVE":
        return { label: "进行中", color: "green" };
      case "PAUSED":
        return { label: "已暂停", color: "orange" };
      case "EXPIRED":
        return { label: "已过期", color: "gray" };
      case "INACTIVE":
        return { label: "已停用", color: "red" };
      default:
        return { label: status, color: "gray" };
    }
  };

  return (
    <div>
      <div style={{ display: "flex", gap: "12px", marginBottom: "16px", flexWrap: "wrap" }}>
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
          onChange={(e) => setStatusFilter(e.target.value)}
          style={{ width: "120px" }}
        >
          <option value="ALL">全部状态</option>
          <option value="ACTIVE">进行中</option>
          <option value="PAUSED">已暂停</option>
          <option value="EXPIRED">已过期</option>
          <option value="INACTIVE">已停用</option>
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

      {!loading && !error && items.length === 0 && (
        <div className="dispatch-empty">暂无固定订餐计划</div>
      )}

      {!loading && !error && items.length > 0 && (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>客户</th>
                <th>电话</th>
                <th>时间段</th>
                <th>午餐</th>
                <th>晚餐</th>
                <th>剩余餐数</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const statusInfo = getStatusLabel(item.status);
                const isLowBalance = item.remainingMeals <= 3;
                const canToggle = item.status === "ACTIVE" || item.status === "PAUSED";
                const isPaused = item.status === "PAUSED" || item.paused;
                return (
                  <tr key={item.id}>
                    <td>{item.customerName}</td>
                    <td>{item.customerPhone}</td>
                    <td>
                      {item.startDate} 至 {item.endDate}
                    </td>
                    <td>
                      {item.lunchEnabled ? (
                        <span className="tag tag-orange">{item.lunchQuantity} 份</span>
                      ) : (
                        <span style={{ color: "var(--text-sub)" }}>-</span>
                      )}
                    </td>
                    <td>
                      {item.dinnerEnabled ? (
                        <span className="tag tag-green">{item.dinnerQuantity} 份</span>
                      ) : (
                        <span style={{ color: "var(--text-sub)" }}>-</span>
                      )}
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
                      <span className={`tag tag-${statusInfo.color}`}>{statusInfo.label}</span>
                    </td>
                    <td>
                      <div style={{ display: "flex", gap: "8px" }}>
                        <button
                          className="btn btn-sm btn-outline"
                          onClick={() => handleEdit(item)}
                          title="编辑"
                        >
                          <Edit size={14} />
                        </button>
                        {canToggle && (
                          <button
                            className="btn btn-sm btn-outline"
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
                          className="btn btn-sm btn-outline"
                          onClick={() => setDeleteTarget(item)}
                          disabled={deletingRuleId === item.id}
                          title="删除"
                          style={{ color: "var(--error-color)" }}
                        >
                          {deletingRuleId === item.id ? "删除中..." : <Trash2 size={14} />}
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
              <span className="delete-confirm-details__value">{getStatusLabel(deleteTarget.status).label}</span>
            </div>
          </div>
        ) : null}
      </AdminDialog>
    </div>
  );
}
