import { createBrowserRouter, Navigate } from "react-router-dom";
import { AdminLayout } from "./layout/AdminLayout";
import { DashboardPage } from "../modules/dashboard/DashboardPage";
import { CustomerAssetPage } from "../modules/customers/CustomerAssetPage";
import { MenuSchedulePage } from "../modules/menu/MenuSchedulePage";
import { OrderPrepPage } from "../modules/orders/OrderPrepPage";
import { DispatchCenterLayout } from "../modules/dispatch/DispatchCenterLayout";
import { DispatchHomePage } from "../modules/dispatch/DispatchHomePage";
import { DispatchAreasPage } from "../modules/dispatch/DispatchAreasPage";
import { DispatchRidersPage } from "../modules/dispatch/DispatchRidersPage";
import { SystemSettingsPage } from "../modules/settings/SystemSettingsPage";
import { OperationsAnalysisPage } from "../modules/analysis/OperationsAnalysisPage";
import { MaintenancePage } from "../modules/maintenance/MaintenancePage";
import { AftersalePage } from "../modules/aftersales/AftersalePage";
import { AdminLoginPage } from "../modules/auth/AdminLoginPage";
import { ADMIN_AUTH_STORAGE_KEY, parseAdminAuthSession } from "../modules/auth/adminAuth.helpers";

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

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <AdminLoginPage />
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
      { path: "dashboard", element: <DashboardPage /> },
      { path: "customers", element: <CustomerAssetPage /> },
      { path: "aftersales", element: <AftersalePage /> },
      { path: "menu", element: <MenuSchedulePage /> },
      { path: "orders", element: <OrderPrepPage /> },
      {
        path: "dispatch",
        element: <DispatchCenterLayout />,
        children: [
          { index: true, element: <DispatchHomePage /> },
          { path: "areas", element: <DispatchAreasPage /> },
          { path: "riders", element: <DispatchRidersPage /> }
        ]
      },
      { path: "analysis", element: <OperationsAnalysisPage /> },
      { path: "settings", element: <SystemSettingsPage /> },
      { path: "maintenance", element: <MaintenancePage /> }
    ]
  }
]);
