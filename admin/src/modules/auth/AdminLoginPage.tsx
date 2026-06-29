import React, { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { KeyRound, ShieldCheck, Smartphone } from "lucide-react";
import { adminLogin } from "../../shared/api/http";
import { SafeInput } from "../../shared/components/SafeInput";
import { toast } from "../../shared/components/Toast";
import {
  ADMIN_AUTH_STORAGE_KEY,
  ADMIN_CREDENTIALS_STORAGE_KEY,
  buildAdminAuthSession,
  buildInitialAdminLoginForm,
  buildSavedAdminCredentials,
  parseAdminAuthSession,
  parseSavedAdminCredentials
} from "./adminAuth.helpers";

function readInitialForm() {
  if (typeof window === "undefined") {
    return buildInitialAdminLoginForm(null);
  }
  const saved = parseSavedAdminCredentials(window.localStorage.getItem(ADMIN_CREDENTIALS_STORAGE_KEY));
  return buildInitialAdminLoginForm(saved);
}

export function AdminLoginPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(readInitialForm);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  function handleForgotPassword() {
    toast("忘记密码请联系管理员协助重置", "error");
  }

  if (typeof window !== "undefined") {
    const existingSession = parseAdminAuthSession(window.localStorage.getItem(ADMIN_AUTH_STORAGE_KEY));
    if (existingSession) {
      return <Navigate to="/dashboard" replace />;
    }
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      const response = await adminLogin(form.phone.trim(), form.password);
      const session = buildAdminAuthSession(response);
      window.localStorage.setItem(ADMIN_AUTH_STORAGE_KEY, JSON.stringify(session));
      const savedCredentials = buildSavedAdminCredentials(form);
      if (savedCredentials) {
        window.localStorage.setItem(ADMIN_CREDENTIALS_STORAGE_KEY, JSON.stringify(savedCredentials));
      } else {
        window.localStorage.removeItem(ADMIN_CREDENTIALS_STORAGE_KEY);
      }
      toast("登录成功");
      navigate("/dashboard", { replace: true });
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || "登录失败，请重试";
      setError(message);
      toast(message, "error");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-card">
        <div className="auth-brand">
          <div className="auth-brand__logo">
            <ShieldCheck size={28} />
          </div>
          <div>
            <h1 className="auth-brand__title">简知轻食商家后台</h1>
            <p className="auth-brand__subtitle">轻食订单、售后和日常运营在这里统一处理。</p>
          </div>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label className="auth-form__field">
            <span>手机号</span>
            <div className="auth-form__input">
              <Smartphone size={16} />
              <SafeInput
                type="tel"
                value={form.phone}
                onValueChange={(value) => setForm((current) => ({ ...current, phone: value }))}
                placeholder="请输入后台手机号"
                autoComplete="username"
              />
            </div>
          </label>

          <label className="auth-form__field">
            <span>密码</span>
            <div className="auth-form__input">
              <KeyRound size={16} />
              <SafeInput
                type="password"
                value={form.password}
                onValueChange={(value) => setForm((current) => ({ ...current, password: value }))}
                placeholder="请输入密码"
                autoComplete="current-password"
              />
            </div>
          </label>

          <label className="auth-form__remember">
            <input
              type="checkbox"
              checked={form.remember}
              onChange={(event) => setForm((current) => ({ ...current, remember: event.target.checked }))}
            />
            <span>记住账号和密码</span>
          </label>

          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "12px", fontSize: "13px", color: "var(--text-sub)" }}>
            <button
              type="button"
              onClick={handleForgotPassword}
              style={{ border: "none", background: "transparent", padding: 0, color: "var(--brand-primary)", cursor: "pointer", fontSize: "13px" }}
            >
              忘记密码
            </button>
            <span>请联系管理员协助重置</span>
          </div>

          {error ? <div className="auth-form__error">{error}</div> : null}

          <button className="btn btn-primary auth-form__submit" type="submit" disabled={submitting}>
            {submitting ? "登录中..." : "登录后台"}
          </button>
        </form>
      </div>
    </div>
  );
}
