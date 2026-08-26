import React, { useEffect, useMemo, useRef, useState } from "react";
import { PlusCircle, Search, Trash2, UserCog } from "lucide-react";
import {
  activateDispatchRider,
  createDispatchRider,
  deleteDispatchRider,
  disableDispatchRider,
  fetchDispatchManagedRiders,
  updateDispatchRiderProfile
} from "../../shared/api/http";
import type { DispatchManagedRiderResponse } from "../../shared/api/types";
import { AppSelect } from "../../shared/components/AppSelect";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { SafeInput } from "../../shared/components/SafeInput";
import { toast } from "../../shared/components/Toast";
import {
  buildCreateRiderPayload,
  createEmptyNewRiderDraft,
  riderStatusLabel,
  riderStatusTagClass,
  validateCreateRiderDraft,
  type NewRiderDraft
} from "./dispatchCenterLayout.helpers";
import { usePersistedState, PAGE_MEMORY_KEYS } from "../../shared/hooks/usePersistedState";

const selectStyle: React.CSSProperties = { width: "100%" };

function getErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message || error?.message || fallback;
}

const STATUS_FILTER_OPTIONS = [
  { label: "全部人员", value: "全部" },
  { label: "启用中", value: "ACTIVE" },
  { label: "已停用", value: "DISABLED" }
];

export function DispatchRidersPage() {
  const nameInputRef = useRef<HTMLInputElement | null>(null);
  const [riders, setRiders] = useState<DispatchManagedRiderResponse[]>([]);
  const [search, setSearch] = usePersistedState<string>(PAGE_MEMORY_KEYS.dispatchRidersSearch, "");
  const [statusFilter, setStatusFilter] = usePersistedState<string>(PAGE_MEMORY_KEYS.dispatchRidersStatus, "全部");
  const [showAddModal, setShowAddModal] = useState(false);
  const [editRider, setEditRider] = useState<DispatchManagedRiderResponse | null>(null);
  const [draft, setDraft] = useState<NewRiderDraft>(createEmptyNewRiderDraft());
  const [saving, setSaving] = useState(false);
  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [deleteConfirmRider, setDeleteConfirmRider] = useState<DispatchManagedRiderResponse | null>(null);
  const [deleting, setDeleting] = useState(false);
  const fieldErrors = validateCreateRiderDraft(draft);
  const canSubmit = !fieldErrors.riderName && !fieldErrors.phone;

  useEffect(() => {
    reload().catch((err) => toast(getErrorMessage(err, "加载骑手列表失败"), "error"));
  }, []);

  useEffect(() => {
    if (!showAddModal || !editRider) {
      return;
    }
    const frameId = window.requestAnimationFrame(() => {
      nameInputRef.current?.focus();
      nameInputRef.current?.select();
    });
    return () => window.cancelAnimationFrame(frameId);
  }, [showAddModal, editRider]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return riders.filter((r) => {
      if (q && !r.riderName.toLowerCase().includes(q) && !(r.phone || "").includes(q)) return false;
      if (statusFilter === "ACTIVE") return r.authStatus === "ACTIVE";
      if (statusFilter === "DISABLED") return r.authStatus !== "ACTIVE";
      return true;
    });
  }, [riders, search, statusFilter]);

  const riderStats = useMemo(() => {
    const active = riders.filter((r) => r.authStatus === "ACTIVE").length;
    const totalTasks = riders.reduce((sum, r) => sum + (r.todayTaskCount ?? 0), 0);
    const totalDelivered = riders.reduce((sum, r) => sum + (r.todayDeliveredCount ?? 0), 0);
    return { totalCount: riders.length, active, totalTasks, totalDelivered };
  }, [riders]);

  async function reload() {
    const data = await fetchDispatchManagedRiders();
    setRiders(data);
  }

  function openAdd() {
    setDraft(createEmptyNewRiderDraft());
    setEditRider(null);
    setShowAddModal(true);
  }

  function openEdit(rider: DispatchManagedRiderResponse) {
    setDraft({
      riderName: rider.riderName,
      phone: rider.phone || "",
      enabled: rider.authStatus === "ACTIVE"
    });
    setEditRider(rider);
    setShowAddModal(true);
  }

  async function handleSave() {
    if (!canSubmit) {
      toast(fieldErrors.riderName || fieldErrors.phone || "请完善骑手信息", "error");
      return;
    }
    setSaving(true);
    try {
      const payload = buildCreateRiderPayload(draft);
      if (editRider) {
        await updateDispatchRiderProfile(editRider.riderId, {
          riderName: payload.riderName,
          displayName: payload.displayName,
          phone: payload.phone,
          areaCode: editRider.areaCode || ""
        });
        if (draft.enabled && editRider.authStatus !== "ACTIVE") {
          await activateDispatchRider(editRider.riderId, {
            riderName: payload.riderName,
            areaCode: editRider.areaCode || ""
          });
        } else if (!draft.enabled && editRider.authStatus === "ACTIVE") {
          await disableDispatchRider(editRider.riderId);
        }
      } else {
        await createDispatchRider(payload);
      }
      setShowAddModal(false);
      await reload();
      toast(editRider ? "骑手信息已更新" : "骑手已创建");
    } catch (err: any) {
      toast(getErrorMessage(err, editRider ? "保存骑手失败" : "创建骑手失败"), "error");
    } finally {
      setSaving(false);
    }
  }

  async function handleToggle(rider: DispatchManagedRiderResponse) {
    setTogglingId(rider.riderId);
    try {
      if (rider.authStatus === "ACTIVE") {
        await disableDispatchRider(rider.riderId);
      } else {
        await activateDispatchRider(rider.riderId, {
          riderName: rider.riderName,
          areaCode: rider.areaCode || ""
        });
      }
      await reload();
    } catch (err: any) {
      toast(getErrorMessage(err, "操作失败"), "error");
    } finally {
      setTogglingId(null);
    }
  }

  async function handleDelete() {
    if (!deleteConfirmRider) return;
    setDeleting(true);
    try {
      await deleteDispatchRider(deleteConfirmRider.riderId);
      setDeleteConfirmRider(null);
      await reload();
      toast("骑手已删除");
    } catch (err: any) {
      toast(getErrorMessage(err, "删除骑手失败"), "error");
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="admin-stack">
      <div className="stat-row">
        <div className="stat-card">
          <div className="stat-title">骑手总数</div>
          <div className="stat-val">{riderStats.totalCount}<span>人</span></div>
          <div className="stat-footer">统一人员档案</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">启用中</div>
          <div className="stat-val">{riderStats.active}<span>人</span></div>
          <div className="stat-footer">可接单骑手</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">今日已送达</div>
          <div className="stat-val">{riderStats.totalDelivered}<span>单</span></div>
          <div className="stat-footer">全部骑手合计</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">今日任务量</div>
          <div className="stat-val">{riderStats.totalTasks}<span>单</span></div>
          <div className="stat-footer">全部骑手合计</div>
        </div>
      </div>

      <div className="toolbar">
        <div className="dispatch-toolbar">
          <div className="dispatch-toolbar__search">
            <SafeInput
              wrapperClassName="dispatch-toolbar__search-wrapper"
              prefix={<Search size={14} />}
              value={search}
              onValueChange={setSearch}
              placeholder="搜索骑手姓名或手机号..."
            />
          </div>
          <div className="dispatch-toolbar__actions">
            <AppSelect
              value={statusFilter}
              options={STATUS_FILTER_OPTIONS}
              onChange={(v) => setStatusFilter(v)}
              style={{ ...selectStyle, width: "160px" }}
            />
            <button className="btn btn-primary" onClick={openAdd}>
              <PlusCircle size={16} /> 新增骑手
            </button>
          </div>
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="dispatch-empty">暂无骑手，点击右上角“新增骑手”开始。</div>
      ) : (
        <div className="table-container">
          <div className="dispatch-table-toolbar">
            <div>
              <div className="dispatch-section__title">骑手列表</div>
              <div className="dispatch-section__note">
                每个骑手唯一建档，负责区域请在「区域管理」中分配。
              </div>
            </div>
            <span className="dispatch-table-toolbar__count">共 {filtered.length} 条结果</span>
          </div>
          <div className="table-responsive table-responsive--fixed-height" style={{ overflowX: "auto" }}>
            <table style={{ minWidth: "860px", width: "100%" }}>
              <thead>
                <tr>
                  <th style={{ width: "120px" }}>姓名</th>
                  <th style={{ width: "150px" }}>手机号</th>
                  <th style={{ width: "120px" }}>状态</th>
                  <th style={{ width: "120px" }}>今日送达/任务</th>
                  <th style={{ width: "180px" }}>最近登录</th>
                  <th style={{ width: "220px" }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((r) => {
                  const busy = togglingId === r.riderId;
                  const active = r.authStatus === "ACTIVE";
                  return (
                    <tr key={r.riderId}>
                      <td><strong>{r.riderName}</strong></td>
                      <td>{r.phone || "--"}</td>
                      <td>
                        <span className={`tag ${riderStatusTagClass(r.authStatus)}`} style={{ fontSize: 11 }}>
                          {riderStatusLabel(r.authStatus)}
                        </span>
                      </td>
                      <td>{r.todayDeliveredCount} / {r.todayTaskCount}</td>
                      <td>{r.lastLoginAt || r.firstLoginAt || "--"}</td>
                      <td style={{ whiteSpace: "nowrap" }}>
                        <div style={{ display: "flex", gap: "6px", flexWrap: "nowrap" }}>
                          <button
                            className={`btn btn-compact ${active ? "btn btn-outline" : "btn btn-primary"}`}
                            disabled={busy}
                            onClick={() => handleToggle(r)}
                            style={{ fontSize: 12, padding: "3px 10px" }}
                          >
                            {busy ? "处理中..." : active ? "停用" : "启用"}
                          </button>
                          <button className="btn btn-outline btn-compact" onClick={() => openEdit(r)}>
                            <UserCog size={14} /> 编辑
                          </button>
                          <button
                            className="btn-delete btn-compact"
                            onClick={() => setDeleteConfirmRider(r)}
                          >
                            <Trash2 size={14} /> 删除
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <AdminDialog
        open={showAddModal}
        title={editRider ? "编辑骑手" : "新增骑手"}
        description={editRider ? "修改骑手姓名、手机号与启用状态。负责区域请在「区域管理」中分配。" : "建档后骑手即可登录接单；负责区域请在「区域管理」中分配。"}
        width={520}
        onClose={saving ? () => undefined : () => setShowAddModal(false)}
        footer={
          <>
            <button className="btn btn-outline" disabled={saving} onClick={() => setShowAddModal(false)}>取消</button>
            <button className="btn btn-primary" disabled={saving || !canSubmit} onClick={handleSave}>
              {saving ? "保存中..." : editRider ? "保存修改" : "创建骑手"}
            </button>
          </>
        }
      >
        <label className="admin-field">
          <span className="admin-field-label">姓名</span>
          <SafeInput
            ref={nameInputRef}
            wrapperClassName={fieldErrors.riderName ? "admin-input--error" : ""}
            value={draft.riderName}
            onValueChange={(value) => setDraft((d) => ({ ...d, riderName: value }))}
            placeholder="骑手全名"
          />
          {fieldErrors.riderName && <div className="form-error">{fieldErrors.riderName}</div>}
        </label>

        <label className="admin-field">
          <span className="admin-field-label">手机号</span>
          <SafeInput
            wrapperClassName={fieldErrors.phone ? "admin-input--error" : ""}
            value={draft.phone}
            onValueChange={(value) => setDraft((d) => ({ ...d, phone: value }))}
            placeholder="后台建档后供骑手登录绑定"
          />
          {fieldErrors.phone && <div className="form-error">{fieldErrors.phone}</div>}
        </label>

        <label className="admin-field" style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 8 }}>
          <input
            type="checkbox"
            checked={draft.enabled}
            onChange={(e) => setDraft((d) => ({ ...d, enabled: e.target.checked }))}
          />
          <span className="admin-field-label" style={{ margin: 0 }}>启用该骑手</span>
        </label>
      </AdminDialog>

      <AdminDialog
        open={!!deleteConfirmRider}
        title="确认删除骑手"
        description={deleteConfirmRider ? `确定要删除骑手"${deleteConfirmRider.riderName}"吗？此操作不可恢复。` : ""}
        width={400}
        onClose={deleting ? () => undefined : () => setDeleteConfirmRider(null)}
        footer={
          <>
            <button className="btn btn-outline" disabled={deleting} onClick={() => setDeleteConfirmRider(null)}>取消</button>
            <button
              className="btn-delete"
              disabled={deleting}
              onClick={handleDelete}
            >
              <Trash2 size={16} />
              {deleting ? "删除中..." : "确认删除"}
            </button>
          </>
        }
      >
        <div style={{ padding: "12px 0", color: "var(--text-sub)" }}>
          删除后，该骑手的所有信息将被永久移除，包括：
          <ul style={{ marginTop: "8px", paddingLeft: "20px" }}>
            <li>账号信息</li>
            <li>区域绑定</li>
            <li>历史派单记录</li>
          </ul>
        </div>
      </AdminDialog>
    </div>
  );
}
