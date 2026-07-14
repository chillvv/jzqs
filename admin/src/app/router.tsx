import { lazy, Suspense } from "react";
import { createBrowserRouter, Navigate, useParams, type RouteObject } from "react-router-dom";
import { AdminLayout } from "./layout/AdminLayout";
import { ADMIN_AUTH_STORAGE_KEY, parseAdminAuthSession } from "../modules/auth/adminAuth.helpers";
import {
  buildSettingsSectionPath,
  DEFAULT_SETTINGS_SECTION,
  isSettingsSection,
  SETTINGS_SECTION
} from "../modules/settings/settingsSections";

// 路由级懒加载：每个页面独立 chunk，首屏只加载布局 + 当前页
const AdminLoginPage = lazy(() => import("../modules/auth/AdminLoginPage").then((m) => ({ default: m.AdminLoginPage })));
const DashboardPage = lazy(() => import("../modules/dashboard/DashboardPage").then((m) => ({ default: m.DashboardPage })));
const CustomerAssetPage = lazy(() => import("../modules/customers/CustomerAssetPage").then((m) => ({ default: m.CustomerAssetPage })));
const MenuSchedulePage = lazy(() => import("../modules/menu/MenuSchedulePage").then((m) => ({ default: m.MenuSchedulePage })));
const OrderPrepPage = lazy(() => import("../modules/orders/OrderPrepPage").then((m) => ({ default: m.OrderPrepPage })));
const DispatchCenterLayout = lazy(() => import("../modules/dispatch/DispatchCenterLayout").then((m) => ({ default: m.DispatchCenterLayout })));
const DispatchHomePage = lazy(() => import("../modules/dispatch/DispatchHomePage").then((m) => ({ default: m.DispatchHomePage })));
const DispatchProgressPage = lazy(() => import("../modules/dispatch/DispatchProgressPage").then((m) => ({ default: m.DispatchProgressPage })));
const DispatchAreasPage = lazy(() => import("../modules/dispatch/DispatchAreasPage").then((m) => ({ default: m.DispatchAreasPage })));
const DispatchRidersPage = lazy(() => import("../modules/dispatch/DispatchRidersPage").then((m) => ({ default: m.DispatchRidersPage })));
const SystemSettingsSectionPage = lazy(() => import("../modules/settings/SystemSettingsSectionPage").then((m) => ({ default: m.SystemSettingsSectionPage })));
const OperationsAnalysisPage = lazy(() => import("../modules/analysis/OperationsAnalysisPage").then((m) => ({ default: m.OperationsAnalysisPage })));
const AftersalePage = lazy(() => import("../modules/aftersales/AftersalePage").then((m) => ({ default: m.AftersalePage })));

function RequireAdminAuth({ children }: { children: JSX.Element }) {
  if (typeof window === "undefined") {
    return children;
  }
  const session = parseAdminAuthSession(window.localStorage.getItem(ADMIN_AUTH_STORAGE_KEY));
  if (!session) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function PageFallback() {
  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: "60vh" }}>
      <div style={{ fontSize: "14px", color: "var(--text-muted)" }}>加载中...</div>
    </div>
  );
}

export function SettingsSectionRoute() {
  const { section } = useParams();
  if (!isSettingsSection(section)) {
    return <Navigate to={buildSettingsSectionPath(DEFAULT_SETTINGS_SECTION)} replace />;
  }
  return (
    <Suspense fallback={<PageFallback />}>
      <SystemSettingsSectionPage />
    </Suspense>
  );
}

export const appRoutes: RouteObject[] = [
  {
    path: "/login",
    element: (
      <Suspense fallback={<PageFallback />}>
        <AdminLoginPage />
      </Suspense>
    )
  },
  {
    path: "/",
    element: (
      <RequireAdminAuth>
        <AdminLayout />
      </RequireAdminAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: "dashboard", element: <Suspense fallback={<PageFallback />}><DashboardPage /></Suspense> },
      { path: "customers", element: <Suspense fallback={<PageFallback />}><CustomerAssetPage /></Suspense> },
      { path: "aftersales", element: <Suspense fallback={<PageFallback />}><AftersalePage /></Suspense> },
      { path: "menu", element: <Suspense fallback={<PageFallback />}><MenuSchedulePage /></Suspense> },
      { path: "orders", element: <Suspense fallback={<PageFallback />}><OrderPrepPage /></Suspense> },
      {
        path: "dispatch",
        element: <Suspense fallback={<PageFallback />}><DispatchCenterLayout /></Suspense>,
        children: [
          { index: true, element: <Suspense fallback={<PageFallback />}><DispatchHomePage /></Suspense> },
          { path: "progress", element: <Suspense fallback={<PageFallback />}><DispatchProgressPage /></Suspense> },
          { path: "areas", element: <Suspense fallback={<PageFallback />}><DispatchAreasPage /></Suspense> },
          { path: "riders", element: <Suspense fallback={<PageFallback />}><DispatchRidersPage /></Suspense> }
        ]
      },
      { path: "analysis", element: <Suspense fallback={<PageFallback />}><OperationsAnalysisPage /></Suspense> },
      { path: "settings", element: <Navigate to={buildSettingsSectionPath(DEFAULT_SETTINGS_SECTION)} replace /> },
      { path: "settings/:section", element: <SettingsSectionRoute /> },
      { path: "maintenance", element: <Navigate to={buildSettingsSectionPath(SETTINGS_SECTION.MAINTENANCE)} replace /> }
    ]
  }
];

export const router = createBrowserRouter(appRoutes);
