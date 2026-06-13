import React, { useEffect, useMemo, useState } from "react";
import { PlusCircle, Search, Trash2, UserCog } from "lucide-react";
import {
  activateDispatchRider,
  createDispatchRider,
  deleteDispatchRider,
  disableDispatchRider,
  fetchDispatchAreaBindings,
  fetchDispatchManagedRiders,
  updateDispatchRiderProfile
} from "../../shared/api/http";
import type {
  DispatchAreaBindingResponse,
  DispatchManagedRiderResponse
} from "../../shared/api/types";
import { AppSelect } from "../../shared/components/AppSelect";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { toast } from "../../shared/components/Toast";
import {
  buildCreateRiderPayload,
  createEmptyNewRiderDraft,
  riderStatusLabel,
  riderStatusTagClass,
  validateCreateRiderDraft,
  DEFAULT_OPERATOR,
  type NewRiderDraft
} from "./dispatchCenterLayout.helpers";

const inputStyle: React.CSSProperties = {
  height: "36px",
  borderRadius: "10px",
  border: "1px solid var(--border-color)",
  padding: "0 10px",
  backgroundColor: "#fff",
  color: "var(--text-main)",
  width: "100%"
};

const selectStyle: React.CSSProperties = { width: "100%" };

function getErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message || error?.message || fallback;
}

export function DispatchRidersPage() {
  const [riders, setRiders] = useState<DispatchManagedRiderResponse[]>([]);
  const [bindings, setBindings] = useState<DispatchAreaBindingResponse[]>([]);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("全部");
  const [showAddModal, setShowAddModal] = useState(false);
  const [editRiderId, setEditRiderId] = useState<number | null>(null);
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

  const riderAreas = useMemo(() => {
    const map = new Map<number, string[]>();
    bindings.forEach((b) => {
      if (b.defaultRiderId !== null && !map.has(b.defaultRiderId)) {
        map.set(b.defaultRiderId, []);
      }
      if (b.defaultRiderId !== null) {
        map.get(b.defaultRiderId)!.push(b.areaCode);
      }
    });
    return map;
  }, [bindings]);

  const areaOptions = useMemo(
    () => [
      { label: "暂不指定区域", value: "" },
      ...bindings.map((b) => ({ label: b.areaCode, value: b.areaCode }))
    ],
    [bindings]
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return riders.filter((r) => {
      if (statusFilter !== "全部" && r.authStatus !== statusFilter) return false;
      if (q && !r.riderName.toLowerCase().includes(q) && !(r.phone || "").includes(q)) return false;
      return true;
    });
  }, [riders, search, statusFilter]);

  const riderStats = useMemo(() => {
    const activeCount = riders.filter((rider) => rider.authStatus === "ACTIVE").length;
    const disabledCount = riders.filter((rider) => rider.authStatus === "DISABLED").length;
    const totalTasks = riders.reduce((sum, rider) => sum + rider.todayTaskCount, 0);
    return {
      totalCount: riders.length,
      activeCount,
      disabledCount,
      totalTasks
    };
  }, [riders]);

  async function reload() {
    const [r, b] = await Promise.all([fetchDispatchManagedRiders(), fetchDispatchAreaBindings()]);
    setRiders(r);
    setBindings(b);
  }

  function openAdd() {
    setDraft(createEmptyNewRiderDraft());
    setEditRiderId(null);
    setShowAddModal(true);
  }

  function openEdit(rider: DispatchManagedRiderResponse) {
    setDraft({
      riderName: rider.riderName,
      phone: rider.phone || "",
      areaCode: rider.areaCode || ""
    });
    setEditRiderId(rider.riderId);
    setShowAddModal(true);
  }

  async function handleSave() {
    if (!canSubmit) {
      toast(fieldErrors.riderName || fieldErrors.phone || "请完善骑手信息", "error");
      return;
    }
    setSaving(true);
    try {
      if (editRiderId) {
        const payload = {
          riderName: draft.riderName.trim(),
          displayName: draft.riderName.trim(),
          phone: draft.phone.trim(),
          areaCode: draft.areaCode.trim(),
          updatedBy: DEFAULT_OPERATOR
        };
        await updateDispatchRiderProfile(editRiderId, payload);
      } else {
        await createDispatchRider(buildCreateRiderPayload(draft));
      }
      setShowAddModal(false);
      await reload();
      toast(editRiderId ? "骑手信息已更新" : "骑手已创建");
    } catch (err: any) {
      toast(getErrorMessage(err, editRiderId ? "保存骑手失败" : "创建骑手失败"), "error");
    } finally {
      setSaving(false);
    }
  }

  async function handleToggle(rider: DispatchManagedRiderResponse) {
    setTogglingId(rider.riderId);
    try {
      if (rider.authStatus === "ACTIVE") {
        await disableDispatchRider(rider.riderId, DEFAULT_OPERATOR);
      } else {
        await activateDispatchRider(rider.riderId, {
          riderName: rider.riderName,
          areaCode: rider.areaCode || "",
          assignedBy: DEFAULT_OPERATOR
        });
      }
      await reload();
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
          <div className="stat-footer">当前已创建账号</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">启用中</div>
          <div className="stat-val">{riderStats.activeCount}<span>人</span></div>
          <div className="stat-footer">可接单骑手</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">已停用</div>
          <div className="stat-val">{riderStats.disabledCount}<span>人</span></div>
          <div className="stat-footer">已暂停派单</div>
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
            <Search size={14} className="dispatch-toolbar__search-icon" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索姓名或手机号"
              style={{ ...inputStyle, paddingLeft: "30px" }}
            />
          </div>
          <div className="dispatch-toolbar__actions">
            <AppSelect
              value={statusFilter}
              options={[
                { label: "全部状态", value: "全部" },
                { label: "启用中", value: "ACTIVE" },
                { label: "已停用", value: "DISABLED" }
              ]}
              onChange={(v) => setStatusFilter(v)}
              style={{ ...selectStyle, width: "140px" }}
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
              <div className="dispatch-section__note">增删改入口统一收在弹窗里，列表只负责查看状态和快速启停。</div>
            </div>
            <span className="dispatch-table-toolbar__count">共 {filtered.length} 条结果</span>
          </div>
          <div className="table-responsive" style={{ overflowX: "auto" }}>
            <table style={{ minWidth: "900px", width: "100%" }}>
              <thead>
                <tr>
                  <th style={{ width: "120px" }}>姓名</th>
                  <th style={{ width: "140px" }}>手机号</th>
                  <th style={{ width: "100px" }}>状态</th>
                  <th style={{ minWidth: "160px" }}>负责区域</th>
                  <th style={{ width: "120px" }}>今日任务</th>
                  <th style={{ width: "180px" }}>最近登录</th>
                  <th style={{ width: "200px" }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((r) => {
                  const areas = riderAreas.get(r.riderId) || [];
                  return (
                    <tr key={r.riderId}>
                      <td><strong>{r.riderName}</strong></td>
                      <td>{r.phone || "--"}</td>
                      <td><span className={`tag ${riderStatusTagClass(r.authStatus)}`}>{riderStatusLabel(r.authStatus)}</span></td>
                      <td>{areas.length > 0 ? areas.join(" / ") : "--"}</td>
                      <td>{r.todayDeliveredCount} / {r.todayTaskCount}</td>
                      <td>{r.lastLoginAt || r.firstLoginAt || "--"}</td>
                      <td style={{ whiteSpace: "nowrap" }}>
                        <div style={{ display: "flex", gap: "6px", flexWrap: "nowrap" }}>
                          <button className="btn btn-outline btn-compact" onClick={() => openEdit(r)}>
                            <UserCog size={14} /> 编辑
                          </button>
                          <button
                            className={`btn btn-compact ${r.authStatus === "ACTIVE" ? "btn btn-outline" : "btn btn-primary"}`}
                            disabled={togglingId === r.riderId}
                            onClick={() => handleToggle(r)}
                          >
                            {r.authStatus === "ACTIVE" ? "停用" : "启用"}
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
        title={editRiderId ? "编辑骑手" : "新增骑手"}
        description={editRiderId ? "修改骑手姓名、登录手机号或负责区域。" : "后台新增骑手后，骑手即可使用对应手机号完成登录绑定。"}
        width={460}
        onClose={saving ? () => undefined : () => setShowAddModal(false)}
        footer={
          <>
            <button className="btn btn-outline" disabled={saving} onClick={() => setShowAddModal(false)}>取消</button>
            <button className="btn btn-primary" disabled={saving || !canSubmit} onClick={handleSave}>
              {saving ? "保存中..." : editRiderId ? "保存修改" : "创建骑手"}
            </button>
          </>
        }
      >
        <label className="admin-field">
          <span className="admin-field-label">姓名</span>
          <input
            className={fieldErrors.riderName ? "admin-input admin-input--error" : "admin-input"}
            value={draft.riderName}
            onChange={(e) => setDraft((d) => ({ ...d, riderName: e.target.value }))}
            placeholder="骑手全名"
            style={{
              ...inputStyle,
              border: fieldErrors.riderName ? "1px solid var(--error-color-dark)" : inputStyle.border
            }}
          />
          {fieldErrors.riderName ? <div className="form-error">{fieldErrors.riderName}</div> : null}
        </label>

        <label className="admin-field">
          <span className="admin-field-label">手机号</span>
          <input
            className={fieldErrors.phone ? "admin-input admin-input--error" : "admin-input"}
            value={draft.phone}
            onChange={(e) => setDraft((d) => ({ ...d, phone: e.target.value }))}
            placeholder="后台建档后供骑手登录绑定"
            style={{
              ...inputStyle,
              border: fieldErrors.phone ? "1px solid var(--error-color-dark)" : inputStyle.border
            }}
          />
          {fieldErrors.phone ? <div className="form-error">{fieldErrors.phone}</div> : null}
        </label>

        <label className="admin-field">
          <span className="admin-field-label">负责区域</span>
          <AppSelect
            value={draft.areaCode}
            options={areaOptions}
            onChange={(value) => setDraft((d) => ({ ...d, areaCode: value }))}
            style={selectStyle}
          />
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
