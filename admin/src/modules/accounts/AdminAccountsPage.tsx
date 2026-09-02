import { useCallback, useEffect, useMemo, useState } from "react";
import { KeyRound, Search, ShieldCheck, UserPlus } from "lucide-react";
import {
  createAdminUser,
  fetchAdminUsers,
  resetAdminUserPassword,
  updateAdminUser,
  deleteAdminUser,
  extractAdminApiErrorMessage
} from "../../shared/api/http";
import type { AdminUserItem } from "../../shared/api/types";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../../shared/components/ui/table";
import { AppSelect } from "../../shared/components/AppSelect";
import { SafeInput } from "../../shared/components/SafeInput";
import { toast } from "../../shared/components/Toast";
import {
  ADMIN_AUTH_STORAGE_KEY,
  parseAdminAuthSession
} from "../auth/adminAuth.helpers";

const ROLE_OPTIONS = [
  { label: "老板（OWNER）", value: "OWNER" },
  { label: "管理员（ADMIN）", value: "ADMIN" },
  { label: "操作员（OPERATOR）", value: "OPERATOR" }
];

const STATUS_OPTIONS = [
  { label: "启用", value: "ENABLED" },
  { label: "停用", value: "DISABLED" }
];

function roleLabel(role: string) {
  const found = ROLE_OPTIONS.find((option) => option.value === role);
  return found ? found.label : role;
}

function statusLabel(status: string) {
  return status === "ENABLED" ? "启用" : "停用";
}

type CreateForm = {
  displayName: string;
  phone: string;
  role: string;
  password: string;
  confirmPassword: string;
};

type EditForm = {
  displayName: string;
  role: string;
  status: string;
};

const emptyCreateForm: CreateForm = {
  displayName: "",
  phone: "",
  role: "OPERATOR",
  password: "",
  confirmPassword: ""
};

export function AdminAccountsPage() {
  const [users, setUsers] = useState<AdminUserItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState<CreateForm>(emptyCreateForm);
  const [submittingCreate, setSubmittingCreate] = useState(false);
  const [editingUser, setEditingUser] = useState<AdminUserItem | null>(null);
  const [editForm, setEditForm] = useState<EditForm>({ displayName: "", role: "OPERATOR", status: "ENABLED" });
  const [submittingEdit, setSubmittingEdit] = useState(false);
  const [resetTarget, setResetTarget] = useState<AdminUserItem | null>(null);
  const [resetPassword, setResetPassword] = useState("");
  const [resetConfirmPassword, setResetConfirmPassword] = useState("");
  const [submittingReset, setSubmittingReset] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<AdminUserItem | null>(null);
  const [deleteConfirmText, setDeleteConfirmText] = useState("");
  const [submittingDelete, setSubmittingDelete] = useState(false);

  const currentRole = useMemo(() => {
    if (typeof window === "undefined") {
      return "";
    }
    const session = parseAdminAuthSession(window.localStorage.getItem(ADMIN_AUTH_STORAGE_KEY));
    return session?.role ?? "";
  }, []);

  // 账号的创建/编辑/重置密码仅老板和管理员可操作（后端同样校验）
  const canManage = currentRole === "OWNER" || currentRole === "ADMIN";

  const loadUsers = useCallback(async () => {
    setLoading(true);
    try {
      const page = await fetchAdminUsers(keyword ? { keyword } : undefined);
      setUsers(page.items || []);
    } catch (err) {
      toast(extractAdminApiErrorMessage(err, "加载账号列表失败"), "error");
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    loadUsers().catch(() => undefined);
  }, [loadUsers]);

  // 切回页面（重新聚焦/切换标签页）时自动刷新列表，
  // 保证多浏览器/多标签页之间的账号数据同步，避免基于过期数据操作报"不存在"
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        loadUsers().catch(() => undefined);
      }
    };
    window.addEventListener("focus", handleVisibilityChange);
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      window.removeEventListener("focus", handleVisibilityChange);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [loadUsers]);

  async function handleCreateSubmit() {
    const displayName = createForm.displayName.trim();
    const phone = createForm.phone.trim();
    if (!displayName || !phone) {
      toast("请填写姓名和手机号", "error");
      return;
    }
    if (!/^\d{11}$/.test(phone)) {
      toast("手机号必须是11位数字", "error");
      return;
    }
    if (createForm.password.length < 6) {
      toast("初始密码至少 6 位", "error");
      return;
    }
    if (createForm.password !== createForm.confirmPassword) {
      toast("两次输入的密码不一致", "error");
      return;
    }

    setSubmittingCreate(true);
    try {
      await createAdminUser({
        displayName,
        phone,
        role: createForm.role,
        password: createForm.password
      });
      toast("账号已创建，该员工可用手机号+密码登录");
      setCreateForm(emptyCreateForm);
      setIsCreateOpen(false);
      await loadUsers();
    } catch (err) {
      toast(extractAdminApiErrorMessage(err, "创建账号失败"), "error");
    } finally {
      setSubmittingCreate(false);
    }
  }

  async function handleEditSubmit() {
    if (!editingUser) {
      return;
    }
    if (!editForm.displayName.trim()) {
      toast("请填写姓名", "error");
      return;
    }
    setSubmittingEdit(true);
    try {
      await updateAdminUser(editingUser.id, {
        displayName: editForm.displayName.trim(),
        role: editForm.role,
        status: editForm.status
      });
      toast("账号已更新");
      setEditingUser(null);
      await loadUsers();
    } catch (err) {
      toast(extractAdminApiErrorMessage(err, "更新账号失败"), "error");
    } finally {
      setSubmittingEdit(false);
    }
  }

  async function handleResetSubmit() {
    if (!resetTarget) {
      return;
    }
    if (resetPassword.length < 6) {
      toast("新密码至少 6 位", "error");
      return;
    }
    if (resetPassword !== resetConfirmPassword) {
      toast("两次输入的密码不一致", "error");
      return;
    }
    setSubmittingReset(true);
    try {
      await resetAdminUserPassword(resetTarget.id, resetPassword);
      toast(`已重置 ${resetTarget.displayName} 的密码`);
      setResetTarget(null);
      setResetPassword("");
      setResetConfirmPassword("");
    } catch (err) {
      toast(extractAdminApiErrorMessage(err, "重置密码失败"), "error");
    } finally {
      setSubmittingReset(false);
    }
  }

  async function handleDeleteSubmit() {
    if (!deleteTarget) {
      return;
    }
    if (deleteConfirmText.trim() !== "删除") {
      toast('请输入"删除"以确认', "error");
      return;
    }
    setSubmittingDelete(true);
    try {
      await deleteAdminUser(deleteTarget.id);
      toast(`已删除账号 ${deleteTarget.displayName}`);
      setDeleteTarget(null);
      setDeleteConfirmText("");
      setEditingUser(null);
      await loadUsers();
    } catch (err) {
      const message = extractAdminApiErrorMessage(err, "删除账号失败");
      if (message.includes("不存在")) {
        // 账号已被其他设备/浏览器删除：刷新列表让过期数据消失，避免用户反复遇到报错
        setDeleteTarget(null);
        setDeleteConfirmText("");
        setEditingUser(null);
        toast("该账号已被删除，列表已刷新", "error");
        await loadUsers();
      } else {
        toast(message, "error");
      }
    } finally {
      setSubmittingDelete(false);
    }
  }

  return (
    <div className="customer-asset-page">
      <div className="page-header">
        <div>
          <h2 className="page-title">后台账号</h2>
          <p className="page-subtitle">
            每位管理人员独立注册账号（姓名+手机号+密码），登录后所有操作自动留痕
            {canManage ? "" : "（账号管理需老板或管理员权限）"}
          </p>
        </div>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <SafeInput
            className="form-control"
            style={{ width: 200 }}
            value={keyword}
            onValueChange={setKeyword}
            placeholder="搜索姓名/手机号"
          />
          <button type="button" className="btn btn-outline" onClick={() => loadUsers().catch(() => undefined)}>
            <Search size={14} /> 搜索
          </button>
          {canManage && (
            <button type="button" className="btn btn-primary" onClick={() => setIsCreateOpen(true)}>
              <UserPlus size={14} /> 新建账号
            </button>
          )}
        </div>
      </div>

      <section className="customer-detail-card customer-detail-card--full">
        {loading ? (
          <div style={{ padding: 24, textAlign: "center", color: "var(--text-muted)" }}>加载中...</div>
        ) : users.length === 0 ? (
          <div style={{ padding: 24, textAlign: "center", color: "var(--text-muted)" }}>暂无账号</div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>姓名</TableHead>
                <TableHead>手机号（登录账号）</TableHead>
                <TableHead>角色</TableHead>
                <TableHead>状态</TableHead>
                {canManage && <TableHead>操作</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.map((user) => (
                <TableRow key={user.id}>
                  <TableCell>
                    <span style={{ fontWeight: 600 }}>{user.displayName}</span>
                    {user.role === "OWNER" && (
                      <span style={{ marginLeft: 6, color: "var(--primary-color)" }}>
                        <ShieldCheck size={13} style={{ verticalAlign: -2 }} />
                      </span>
                    )}
                  </TableCell>
                  <TableCell>{user.phone}</TableCell>
                  <TableCell>{roleLabel(user.role)}</TableCell>
                  <TableCell>
                    <span style={{ color: user.status === "ENABLED" ? "#16a34a" : "#dc2626", fontWeight: 600 }}>
                      {statusLabel(user.status)}
                    </span>
                  </TableCell>
                  {canManage && (
                    <TableCell>
                      <div style={{ display: "flex", gap: 8 }}>
                        <button
                          type="button"
                          className="btn btn-outline"
                          onClick={() => {
                            setEditingUser(user);
                            setEditForm({
                              displayName: user.displayName,
                              role: user.role,
                              status: user.status
                            });
                          }}
                        >
                          编辑
                        </button>
                        <button
                          type="button"
                          className="btn btn-outline"
                          onClick={() => {
                            setResetTarget(user);
                            setResetPassword("");
                            setResetConfirmPassword("");
                          }}
                        >
                          <KeyRound size={13} /> 重置密码
                        </button>
                      </div>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>

      {isCreateOpen && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>新建管理账号</span>
              <span className="modal-close" onClick={() => setIsCreateOpen(false)}>×</span>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label">姓名</label>
                <SafeInput
                  className="form-control"
                  value={createForm.displayName}
                  onValueChange={(value) => setCreateForm((current) => ({ ...current, displayName: value }))}
                  placeholder="请输入真实姓名（操作留痕展示用）"
                />
              </div>
              <div className="form-group">
                <label className="form-label">手机号（登录账号，创建后不可修改）</label>
                <SafeInput
                  className="form-control"
                  value={createForm.phone}
                  onValueChange={(value) => setCreateForm((current) => ({ ...current, phone: value }))}
                  placeholder="11位手机号"
                />
              </div>
              <div className="form-group">
                <label className="form-label">角色</label>
                <AppSelect
                  value={createForm.role}
                  options={ROLE_OPTIONS}
                  onChange={(value) => setCreateForm((current) => ({ ...current, role: value }))}
                />
              </div>
              <div className="form-group">
                <label className="form-label">初始密码（至少6位，请告知本人）</label>
                <SafeInput
                  className="form-control"
                  type="password"
                  value={createForm.password}
                  onValueChange={(value) => setCreateForm((current) => ({ ...current, password: value }))}
                  placeholder="至少6位"
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">确认初始密码</label>
                <SafeInput
                  className="form-control"
                  type="password"
                  value={createForm.confirmPassword}
                  onValueChange={(value) => setCreateForm((current) => ({ ...current, confirmPassword: value }))}
                  placeholder="再次输入"
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setIsCreateOpen(false)}>取消</button>
              <button
                className="btn btn-primary"
                disabled={submittingCreate}
                onClick={() => handleCreateSubmit().catch(() => undefined)}
              >
                {submittingCreate ? "创建中..." : "创建账号"}
              </button>
            </div>
          </div>
        </div>
      )}

      {editingUser && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>编辑账号：{editingUser.displayName}</span>
              <span className="modal-close" onClick={() => setEditingUser(null)}>×</span>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label">手机号（登录账号，不可修改）</label>
                <SafeInput
                  className="form-control"
                  value={editingUser.phone}
                  disabled
                  onValueChange={() => undefined}
                />
              </div>
              <div className="form-group">
                <label className="form-label">姓名</label>
                <SafeInput
                  className="form-control"
                  value={editForm.displayName}
                  onValueChange={(value) => setEditForm((current) => ({ ...current, displayName: value }))}
                />
              </div>
              <div className="form-group">
                <label className="form-label">角色</label>
                <AppSelect
                  value={editForm.role}
                  options={ROLE_OPTIONS}
                  onChange={(value) => setEditForm((current) => ({ ...current, role: value }))}
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">状态</label>
                <AppSelect
                  value={editForm.status}
                  options={STATUS_OPTIONS}
                  onChange={(value) => setEditForm((current) => ({ ...current, status: value }))}
                />
              </div>
            </div>
            <div className="modal-footer">
              <button
                type="button"
                className="btn btn-danger"
                style={{ marginRight: "auto" }}
                onClick={() => {
                  setDeleteTarget(editingUser);
                  setDeleteConfirmText("");
                }}
              >
                删除账号
              </button>
              <button className="btn btn-outline" onClick={() => setEditingUser(null)}>取消</button>
              <button
                className="btn btn-primary"
                disabled={submittingEdit}
                onClick={() => handleEditSubmit().catch(() => undefined)}
              >
                {submittingEdit ? "保存中..." : "保存"}
              </button>
            </div>
          </div>
        </div>
      )}

      {resetTarget && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>重置密码：{resetTarget.displayName}（{resetTarget.phone}）</span>
              <span className="modal-close" onClick={() => setResetTarget(null)}>×</span>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label">新密码（至少6位）</label>
                <SafeInput
                  className="form-control"
                  type="password"
                  value={resetPassword}
                  onValueChange={setResetPassword}
                  placeholder="至少6位"
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">确认新密码</label>
                <SafeInput
                  className="form-control"
                  type="password"
                  value={resetConfirmPassword}
                  onValueChange={setResetConfirmPassword}
                  placeholder="再次输入"
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setResetTarget(null)}>取消</button>
              <button
                className="btn btn-primary"
                disabled={submittingReset}
                onClick={() => handleResetSubmit().catch(() => undefined)}
              >
                {submittingReset ? "重置中..." : "确认重置"}
              </button>
            </div>
          </div>
        </div>
      )}

      {deleteTarget && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>确认删除账号</span>
              <span className="modal-close" onClick={() => setDeleteTarget(null)}>×</span>
            </div>
            <div className="modal-body">
              <p style={{ margin: "0 0 12px", color: "var(--text-muted)" }}>
                此操作将<strong style={{ color: "#dc2626" }}>彻底删除</strong>账号
                <strong> {deleteTarget.displayName}（{deleteTarget.phone}）</strong>，
                删除后该账号无法登录，且不可恢复。
              </p>
              <p style={{ margin: "0 0 8px" }}>
                请输入 <strong style={{ color: "#dc2626" }}>删除</strong> 以确认：
              </p>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <SafeInput
                  className="form-control"
                  value={deleteConfirmText}
                  onValueChange={setDeleteConfirmText}
                  placeholder="请输入：删除"
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setDeleteTarget(null)}>取消</button>
              <button
                type="button"
                className="btn btn-danger"
                disabled={submittingDelete || deleteConfirmText.trim() !== "删除"}
                onClick={() => handleDeleteSubmit().catch(() => undefined)}
              >
                {submittingDelete ? "删除中..." : "确认删除"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
