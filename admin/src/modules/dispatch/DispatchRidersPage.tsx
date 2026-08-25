import React, { useEffect, useMemo, useRef, useState } from "react";
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
import { SafeInput } from "../../shared/components/SafeInput";
import { toast } from "../../shared/components/Toast";
import {
  createEmptyNewRiderDraft,
  riderStatusLabel,
  riderStatusTagClass,
  validateCreateRiderDraft,
  mergeRidersByMealPeriod,
  type DispatchMealPeriod,
  type MergedRider,
  type NewRiderDraft
} from "./dispatchCenterLayout.helpers";
import { usePersistedState, PAGE_MEMORY_KEYS } from "../../shared/hooks/usePersistedState";

const selectStyle: React.CSSProperties = { width: "100%" };

function getErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message || error?.message || fallback;
}

const STATUS_FILTER_OPTIONS = [
  { label: "全部人员", value: "全部" },
  { label: "午餐启用中", value: "LUNCH_ACTIVE" },
  { label: "午餐已停用", value: "LUNCH_DISABLED" },
  { label: "晚餐启用中", value: "DINNER_ACTIVE" },
  { label: "晚餐已停用", value: "DINNER_DISABLED" }
];

export function DispatchRidersPage() {
  const nameInputRef = useRef<HTMLInputElement | null>(null);
  const [lunchRiders, setLunchRiders] = useState<DispatchManagedRiderResponse[]>([]);
  const [dinnerRiders, setDinnerRiders] = useState<DispatchManagedRiderResponse[]>([]);
  const [lunchBindings, setLunchBindings] = useState<DispatchAreaBindingResponse[]>([]);
  const [dinnerBindings, setDinnerBindings] = useState<DispatchAreaBindingResponse[]>([]);
  const [search, setSearch] = usePersistedState<string>(PAGE_MEMORY_KEYS.dispatchRidersSearch, "");
  const [statusFilter, setStatusFilter] = usePersistedState<string>(PAGE_MEMORY_KEYS.dispatchRidersStatus, "全部");
  const [showAddModal, setShowAddModal] = useState(false);
  const [editRider, setEditRider] = useState<MergedRider | null>(null);
  const [draft, setDraft] = useState<NewRiderDraft>(createEmptyNewRiderDraft());
  const [saving, setSaving] = useState(false);
  const [togglingKey, setTogglingKey] = useState<string | null>(null);
  const [deleteConfirmRider, setDeleteConfirmRider] = useState<MergedRider | null>(null);
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

  // 合并午餐/晚餐骑手为人员维度列表
  const mergedRiders = useMemo(
    () => mergeRidersByMealPeriod(lunchRiders, dinnerRiders),
    [lunchRiders, dinnerRiders]
  );

  const lunchAreaOptions = useMemo(
    () => [
      { label: "暂不指定区域", value: "" },
      ...lunchBindings.map((b) => ({ label: b.areaCode, value: b.areaCode }))
    ],
    [lunchBindings]
  );

  const dinnerAreaOptions = useMemo(
    () => [
      { label: "暂不指定区域", value: "" },
      ...dinnerBindings.map((b) => ({ label: b.areaCode, value: b.areaCode }))
    ],
    [dinnerBindings]
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return mergedRiders.filter((m) => {
      if (q && !m.riderName.toLowerCase().includes(q) && !(m.phone || "").includes(q)) return false;
      switch (statusFilter) {
        case "LUNCH_ACTIVE":
          return m.lunch?.authStatus === "ACTIVE";
        case "LUNCH_DISABLED":
          return m.lunch != null && m.lunch.authStatus !== "ACTIVE";
        case "DINNER_ACTIVE":
          return m.dinner?.authStatus === "ACTIVE";
        case "DINNER_DISABLED":
          return m.dinner != null && m.dinner.authStatus !== "ACTIVE";
        default:
          return true;
      }
    });
  }, [mergedRiders, search, statusFilter]);

  const riderStats = useMemo(() => {
    const lunchActive = mergedRiders.filter((m) => m.lunch?.authStatus === "ACTIVE").length;
    const dinnerActive = mergedRiders.filter((m) => m.dinner?.authStatus === "ACTIVE").length;
    const totalTasks = mergedRiders.reduce(
      (sum, m) => sum + (m.lunch?.todayTaskCount ?? 0) + (m.dinner?.todayTaskCount ?? 0),
      0
    );
    return {
      totalCount: mergedRiders.length,
      lunchActive,
      dinnerActive,
      totalTasks
    };
  }, [mergedRiders]);

  async function reload() {
    const [lunchR, dinnerR, lunchB, dinnerB] = await Promise.all([
      fetchDispatchManagedRiders({ mealPeriod: "LUNCH" }),
      fetchDispatchManagedRiders({ mealPeriod: "DINNER" }),
      fetchDispatchAreaBindings("LUNCH"),
      fetchDispatchAreaBindings("DINNER")
    ]);
    setLunchRiders(lunchR);
    setDinnerRiders(dinnerR);
    setLunchBindings(lunchB);
    setDinnerBindings(dinnerB);
  }

  function openAdd() {
    setDraft(createEmptyNewRiderDraft());
    setEditRider(null);
    setShowAddModal(true);
  }

  function openEdit(rider: MergedRider) {
    setDraft({
      riderName: rider.riderName,
      phone: rider.phone || "",
      lunchEnabled: rider.lunch?.authStatus === "ACTIVE",
      lunchAreaCode: rider.lunch?.areaCode || "",
      dinnerEnabled: rider.dinner?.authStatus === "ACTIVE",
      dinnerAreaCode: rider.dinner?.areaCode || ""
    });
    setEditRider(rider);
    setShowAddModal(true);
  }

  async function saveMeal(
    meal: DispatchMealPeriod,
    existing: DispatchManagedRiderResponse | null,
    enabled: boolean,
    areaCode: string,
    riderName: string,
    phone: string
  ) {
    const base = {
      mealPeriod: meal,
      riderName,
      displayName: riderName,
      phone,
      areaCode: areaCode.trim()
    };
    if (existing) {
      // 已建档：先同步基础信息，再按需启用/停用
      await updateDispatchRiderProfile(existing.riderId, base);
      if (enabled && existing.authStatus !== "ACTIVE") {
        await activateDispatchRider(existing.riderId, { riderName, areaCode: areaCode.trim() });
      } else if (!enabled && existing.authStatus === "ACTIVE") {
        await disableDispatchRider(existing.riderId);
      }
    } else if (enabled) {
      await createDispatchRider({
        mealPeriod: meal,
        riderName,
        displayName: riderName,
        phone,
        areaCode: areaCode.trim() || undefined,
        employmentStatus: "ACTIVE"
      });
    }
  }

  async function handleSave() {
    if (!canSubmit) {
      toast(fieldErrors.riderName || fieldErrors.phone || "请完善骑手信息", "error");
      return;
    }
    setSaving(true);
    try {
      const riderName = draft.riderName.trim();
      const phone = draft.phone.trim();
      if (editRider) {
        await saveMeal("LUNCH", editRider.lunch, draft.lunchEnabled, draft.lunchAreaCode, riderName, phone);
        await saveMeal("DINNER", editRider.dinner, draft.dinnerEnabled, draft.dinnerAreaCode, riderName, phone);
      } else {
        if (draft.lunchEnabled) {
          await createDispatchRider({
            mealPeriod: "LUNCH",
            riderName,
            displayName: riderName,
            phone,
            areaCode: draft.lunchAreaCode.trim() || undefined,
            employmentStatus: "ACTIVE"
          });
        }
        if (draft.dinnerEnabled) {
          await createDispatchRider({
            mealPeriod: "DINNER",
            riderName,
            displayName: riderName,
            phone,
            areaCode: draft.dinnerAreaCode.trim() || undefined,
            employmentStatus: "ACTIVE"
          });
        }
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

  async function handleToggleMeal(rider: MergedRider, meal: DispatchMealPeriod) {
    const target = meal === "LUNCH" ? rider.lunch : rider.dinner;
    if (!target) return;
    const key = `${meal}:${rider.riderName}`;
    setTogglingKey(key);
    try {
      if (target.authStatus === "ACTIVE") {
        await disableDispatchRider(target.riderId);
      } else {
        await activateDispatchRider(target.riderId, {
          riderName: target.riderName,
          areaCode: target.areaCode || ""
        });
      }
      await reload();
    } catch (err: any) {
      toast(getErrorMessage(err, "操作失败"), "error");
    } finally {
      setTogglingKey(null);
    }
  }

  async function handleDelete() {
    if (!deleteConfirmRider) return;
    const target = deleteConfirmRider.lunch ?? deleteConfirmRider.dinner;
    if (!target) return;
    setDeleting(true);
    try {
      await deleteDispatchRider(target.riderId);
      setDeleteConfirmRider(null);
      await reload();
      toast("骑手已删除");
    } catch (err: any) {
      toast(getErrorMessage(err, "删除骑手失败"), "error");
    } finally {
      setDeleting(false);
    }
  }

  function renderMealCell(rider: MergedRider, meal: DispatchMealPeriod) {
    const record = meal === "LUNCH" ? rider.lunch : rider.dinner;
    const key = `${meal}:${rider.riderName}`;
    const busy = togglingKey === key;
    if (!record) {
      return (
        <div style={{ display: "flex", flexDirection: "column", gap: 4, alignItems: "flex-start" }}>
          <span className="tag tag-gray" style={{ fontSize: 11 }}>未建档</span>
        </div>
      );
    }
    const active = record.authStatus === "ACTIVE";
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: 4, alignItems: "flex-start" }}>
        <span className={`tag ${riderStatusTagClass(record.authStatus)}`} style={{ fontSize: 11 }}>
          {riderStatusLabel(record.authStatus)}
        </span>
        <button
          className={`btn btn-compact ${active ? "btn btn-outline" : "btn btn-primary"}`}
          disabled={busy}
          onClick={() => handleToggleMeal(rider, meal)}
          style={{ fontSize: 12, padding: "3px 10px" }}
        >
          {busy ? "处理中..." : active ? "停用" : "启用"}
        </button>
      </div>
    );
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
          <div className="stat-title">午餐启用</div>
          <div className="stat-val">{riderStats.lunchActive}<span>人</span></div>
          <div className="stat-footer">午餐可接单骑手</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">晚餐启用</div>
          <div className="stat-val">{riderStats.dinnerActive}<span>人</span></div>
          <div className="stat-footer">晚餐可接单骑手</div>
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
                同一骑手统一为一条人员档案，午餐、晚餐的启用状态与负责区域分别维护。
              </div>
            </div>
            <span className="dispatch-table-toolbar__count">共 {filtered.length} 条结果</span>
          </div>
          <div className="table-responsive table-responsive--fixed-height" style={{ overflowX: "auto" }}>
            <table style={{ minWidth: "1000px", width: "100%" }}>
              <thead>
                <tr>
                  <th style={{ width: "120px" }}>姓名</th>
                  <th style={{ width: "140px" }}>手机号</th>
                  <th style={{ width: "140px" }}>午餐</th>
                  <th style={{ width: "140px" }}>晚餐</th>
                  <th style={{ width: "120px" }}>今日任务</th>
                  <th style={{ width: "180px" }}>最近登录</th>
                  <th style={{ width: "160px" }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((m) => {
                  const totalDelivered = (m.lunch?.todayDeliveredCount ?? 0) + (m.dinner?.todayDeliveredCount ?? 0);
                  const totalTask = (m.lunch?.todayTaskCount ?? 0) + (m.dinner?.todayTaskCount ?? 0);
                  const lastLogin = m.lunch?.lastLoginAt || m.dinner?.lastLoginAt || m.lunch?.firstLoginAt || m.dinner?.firstLoginAt || "--";
                  return (
                    <tr key={m.riderName}>
                      <td><strong>{m.riderName}</strong></td>
                      <td>{m.phone || "--"}</td>
                      <td>{renderMealCell(m, "LUNCH")}</td>
                      <td>{renderMealCell(m, "DINNER")}</td>
                      <td>{totalDelivered} / {totalTask}</td>
                      <td>{lastLogin}</td>
                      <td style={{ whiteSpace: "nowrap" }}>
                        <div style={{ display: "flex", gap: "6px", flexWrap: "nowrap" }}>
                          <button className="btn btn-outline btn-compact" onClick={() => openEdit(m)}>
                            <UserCog size={14} /> 编辑
                          </button>
                          <button
                            className="btn-delete btn-compact"
                            onClick={() => setDeleteConfirmRider(m)}
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
        description={editRider ? "修改骑手基础信息，并分别维护午餐/晚餐的启用状态与负责区域。" : "一次建档，自动可用于午餐和晚餐；如某餐不使用可取消勾选。"}
        width={640}
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

        <div style={{ display: "grid", gap: "12px", marginTop: 4 }}>
          <div style={{ border: "1px solid #e3e6ee", borderRadius: 8, padding: "10px 12px" }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 8 }}>
              <strong>午餐</strong>
              <label style={{ display: "flex", alignItems: "center", gap: 6, cursor: "pointer", fontSize: 13 }}>
                <input
                  type="checkbox"
                  checked={draft.lunchEnabled}
                  onChange={(e) => setDraft((d) => ({ ...d, lunchEnabled: e.target.checked }))}
                />
                启用
              </label>
            </div>
            <AppSelect
              value={draft.lunchAreaCode}
              options={lunchAreaOptions}
              placeholder="午餐负责区域"
              disabled={!draft.lunchEnabled}
              onChange={(value) => setDraft((d) => ({ ...d, lunchAreaCode: value }))}
              style={selectStyle}
            />
          </div>

          <div style={{ border: "1px solid #e3e6ee", borderRadius: 8, padding: "10px 12px" }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 8 }}>
              <strong>晚餐</strong>
              <label style={{ display: "flex", alignItems: "center", gap: 6, cursor: "pointer", fontSize: 13 }}>
                <input
                  type="checkbox"
                  checked={draft.dinnerEnabled}
                  onChange={(e) => setDraft((d) => ({ ...d, dinnerEnabled: e.target.checked }))}
                />
                启用
              </label>
            </div>
            <AppSelect
              value={draft.dinnerAreaCode}
              options={dinnerAreaOptions}
              placeholder="晚餐负责区域"
              disabled={!draft.dinnerEnabled}
              onChange={(value) => setDraft((d) => ({ ...d, dinnerAreaCode: value }))}
              style={selectStyle}
            />
          </div>
        </div>
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
          删除后，该骑手（含午餐、晚餐档案）的所有信息将被永久移除，包括：
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
