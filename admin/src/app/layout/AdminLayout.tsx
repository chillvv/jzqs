import React, { useEffect, useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  LayoutDashboard,
  ClipboardList,
  Bike,
  CreditCard,
  CalendarDays,
  Settings,
  UtensilsCrossed,
  LineChart,
  Wrench,
  LifeBuoy,
  Menu,
} from "lucide-react";
import "../../index.css";
import { changeAdminPassword, fetchAdminProfile, logoutAdmin } from "../../shared/api/http";
import { toast } from "../../shared/components/Toast";
import { useScale } from "../../shared/hooks/useScale";
import { ToastContainer } from "../../shared/components/Toast";
import {
  ADMIN_AUTH_STORAGE_KEY,
  ADMIN_CREDENTIALS_STORAGE_KEY,
  buildAdminAuthSession,
  buildSavedAdminCredentials,
  parseAdminAuthSession,
  parseSavedAdminCredentials,
  type AdminAuthSession
} from "../../modules/auth/adminAuth.helpers";

const items = [
  { key: "/dashboard", label: "看板数据", shortLabel: "看板", description: "核心经营指标与趋势", icon: <LayoutDashboard size={18} /> },
  { key: "/orders", label: "订单运营", shortLabel: "运营", description: "备餐、固定订餐、订单处理", icon: <ClipboardList size={18} /> },
  { key: "/aftersales", label: "售后台账", shortLabel: "售后", description: "登记、结算、补偿与退款对账", icon: <LifeBuoy size={18} /> },
  { key: "/dispatch", label: "骑手中心", shortLabel: "骑手", description: "派单、轨迹、回执与通知", icon: <Bike size={18} /> },
  { key: "/customers", label: "客户资产", shortLabel: "客户", description: "余额、流水、重点客户", icon: <CreditCard size={18} /> },
  { key: "/menu", label: "菜单配置", shortLabel: "菜单", description: "周菜单、排菜与发布", icon: <CalendarDays size={18} /> },
  { key: "/analysis", label: "经营分析", shortLabel: "分析", description: "营收、成本、毛利洞察", icon: <LineChart size={18} /> },
  { key: "/settings", label: "系统设置", shortLabel: "设置", description: "锁定公告与轮播图", icon: <Settings size={18} /> },
  { key: "/maintenance", label: "系统维护", shortLabel: "维护", description: "数据清理与系统维护", icon: <Wrench size={18} /> },
];

export function AdminLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const scaleStyle = useScale(1440, 768);
  const [session, setSession] = useState<AdminAuthSession | null>(() => {
    if (typeof window === "undefined") {
      return null;
    }
    return parseAdminAuthSession(window.localStorage.getItem(ADMIN_AUTH_STORAGE_KEY));
  });
  const [isPasswordOpen, setIsPasswordOpen] = useState(false);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [passwordForm, setPasswordForm] = useState({
    oldPassword: "",
    newPassword: "",
    confirmPassword: ""
  });
  const [savingPassword, setSavingPassword] = useState(false);
  const sessionToken = session?.token ?? "";

  useEffect(() => {
    if (!sessionToken) {
      navigate("/login", { replace: true });
      return;
    }

    let cancelled = false;
    fetchAdminProfile()
      .then((profile) => {
        if (cancelled) {
          return;
        }
        setSession((current) => {
          if (!current) {
            return current;
          }
          const nextSession = buildAdminAuthSession({
            ...current,
            ...profile,
            token: sessionToken
          });
          window.localStorage.setItem(ADMIN_AUTH_STORAGE_KEY, JSON.stringify(nextSession));
          return nextSession;
        });
      })
      .catch(() => {
        if (cancelled) {
          return;
        }
        window.localStorage.removeItem(ADMIN_AUTH_STORAGE_KEY);
        setSession(null);
        toast("登录已失效，请重新登录", "error");
        navigate("/login", { replace: true });
      });
    return () => {
      cancelled = true;
    };
  }, [navigate, sessionToken]);

  useEffect(() => {
    setMobileSidebarOpen(false);
  }, [location.pathname]);

  async function handleLogout() {
    try {
      await logoutAdmin();
    } catch {
      // Ignore logout failures and clear the local session anyway.
    }
    window.localStorage.removeItem(ADMIN_AUTH_STORAGE_KEY);
    setSession(null);
    navigate("/login", { replace: true });
  }

  async function handlePasswordSubmit() {
    const oldPassword = passwordForm.oldPassword.trim();
    const newPassword = passwordForm.newPassword.trim();
    if (!oldPassword || !newPassword) {
      toast("请填写完整旧密码和新密码", "error");
      return;
    }
    if (newPassword.length < 6) {
      toast("新密码至少 6 位", "error");
      return;
    }
    if (newPassword !== passwordForm.confirmPassword.trim()) {
      toast("两次输入的新密码不一致", "error");
      return;
    }

    setSavingPassword(true);
    try {
      await changeAdminPassword(oldPassword, newPassword);
      const remembered = parseSavedAdminCredentials(window.localStorage.getItem(ADMIN_CREDENTIALS_STORAGE_KEY));
      if (remembered && session && remembered.phone === session.phone) {
        const nextCredentials = buildSavedAdminCredentials({
          phone: remembered.phone,
          password: newPassword,
          remember: true
        });
        if (nextCredentials) {
          window.localStorage.setItem(ADMIN_CREDENTIALS_STORAGE_KEY, JSON.stringify(nextCredentials));
        }
      }
      setPasswordForm({
        oldPassword: "",
        newPassword: "",
        confirmPassword: ""
      });
      setIsPasswordOpen(false);
      toast("密码已更新");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "修改密码失败", "error");
    } finally {
      setSavingPassword(false);
    }
  }

  return (
    <div className="admin-shell" style={scaleStyle}>
      <ToastContainer />
      <button
        type="button"
        className="mobile-sidebar-toggle"
        onClick={() => setMobileSidebarOpen((current) => !current)}
      >
        <Menu size={18} />
        菜单
      </button>
      {mobileSidebarOpen ? (
        <button
          type="button"
          className="mobile-sidebar-backdrop"
          onClick={() => setMobileSidebarOpen(false)}
          aria-label="关闭侧边栏"
        />
      ) : null}
      <div className={`sidebar ${mobileSidebarOpen ? "sidebar--mobile-open" : ""}`}>
        <div className="logo-area">
          <UtensilsCrossed size={24} strokeWidth={2.5} />
          <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
            <span>简知轻食</span>
            <span style={{ fontSize: "11px", color: "var(--text-muted)", fontWeight: 600, letterSpacing: "0.04em" }}>
              商家后台
            </span>
          </div>
        </div>

        <div className="nav-menu">
          {items.map((item) => (
            <Link
              key={item.key}
              to={item.key}
              className={`nav-item ${location.pathname.startsWith(item.key) ? "active" : ""}`}
            >
              <span className="nav-icon">{item.icon}</span>
              <span style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                <span>{item.label}</span>
                <span style={{ fontSize: "11px", color: location.pathname.startsWith(item.key) ? "rgba(37,99,235,0.8)" : "var(--text-muted)", fontWeight: 500 }}>
                  {item.description}
                </span>
              </span>
            </Link>
          ))}
        </div>

        {/* 账号信息移到左侧边栏底部 */}
        <div className="sidebar-user">
          <div className="avatar">{session?.displayName?.slice(0, 1) || "管"}</div>
          <div className="sidebar-user__meta">
            <span className="sidebar-user__name">{session?.displayName || "商家后台"}</span>
            <span className="sidebar-user__phone">{session?.phone || "未登录"}</span>
          </div>
          <div className="sidebar-user__actions">
            <button type="button" className="sidebar-user__button" onClick={() => setIsPasswordOpen(true)}>
              改密
            </button>
            <button type="button" className="sidebar-user__button danger" onClick={() => handleLogout().catch(() => undefined)}>
              退出
            </button>
          </div>
        </div>
      </div>

      <div className="mobile-tab-bar">
        {items.map((item) => (
          <Link
            key={item.key}
            to={item.key}
            className={`mobile-tab-item ${location.pathname.startsWith(item.key) ? "active" : ""}`}
          >
            <span className="mobile-tab-icon">{item.icon}</span>
            <span>{item.shortLabel}</span>
          </Link>
        ))}
      </div>

      <div className="main-content">
        <div className="content-scroll">
          <Outlet />
        </div>
      </div>

      {isPasswordOpen && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>修改后台密码</span>
              <span className="modal-close" onClick={() => setIsPasswordOpen(false)}>
                ×
              </span>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label">当前密码</label>
                <input
                  className="form-control"
                  type="password"
                  value={passwordForm.oldPassword}
                  onChange={(event) => setPasswordForm((current) => ({ ...current, oldPassword: event.target.value }))}
                  placeholder="请输入当前密码"
                />
              </div>
              <div className="form-group">
                <label className="form-label">新密码</label>
                <input
                  className="form-control"
                  type="password"
                  value={passwordForm.newPassword}
                  onChange={(event) => setPasswordForm((current) => ({ ...current, newPassword: event.target.value }))}
                  placeholder="请输入新密码"
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">确认新密码</label>
                <input
                  className="form-control"
                  type="password"
                  value={passwordForm.confirmPassword}
                  onChange={(event) => setPasswordForm((current) => ({ ...current, confirmPassword: event.target.value }))}
                  placeholder="再次输入新密码"
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setIsPasswordOpen(false)}>
                取消
              </button>
              <button className="btn btn-primary" onClick={() => handlePasswordSubmit().catch(() => undefined)} disabled={savingPassword}>
                {savingPassword ? "提交中..." : "确认修改"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
