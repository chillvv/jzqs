import { NavLink, Outlet, useLocation } from "react-router-dom";
import { LayoutDashboard, MapPinned, UserCog } from "lucide-react";
import { buildDispatchWorkspaceNav, DispatchMealPeriod, mealPeriodLabel } from "./dispatchCenterLayout.helpers";
import { DispatchProvider, useDispatchContext } from "./DispatchContext";
import { DatePicker } from "../../shared/components/DatePicker";

const navIcons: Record<string, React.ComponentType<{ size?: number | string }>> = {
  "配送工作台": LayoutDashboard,
  "区域管理": MapPinned,
  "骑手管理": UserCog
};

// 骑手管理页不需要日期/餐期筛选
const DATE_FILTER_HIDDEN_PATHS = ["/dispatch/riders"];

function DispatchCenterInner() {
  const navItems = buildDispatchWorkspaceNav();
  const { serveDate, setServeDate, mealPeriod, setMealPeriod } = useDispatchContext();
  const location = useLocation();
  const showDateFilter = !DATE_FILTER_HIDDEN_PATHS.includes(location.pathname);

  return (
    <div className="dispatch-workspace">
      <div className="page-header">
        <div>
          <h2 className="page-title">骑手配送中心</h2>
          <p className="page-subtitle">区域管订单，骑手管配送</p>
        </div>
        {showDateFilter && (
          <div className="dispatch-global-filter">
            <DatePicker value={serveDate} onChange={setServeDate} showTomorrowShortcut={false} />
            {(["LUNCH", "DINNER"] as DispatchMealPeriod[]).map((value) => (
              <button
                key={value}
                type="button"
                className={value === mealPeriod ? "btn btn-primary btn-compact" : "btn btn-outline btn-compact"}
                onClick={() => setMealPeriod(value)}
              >
                {mealPeriodLabel(value)}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="dispatch-subnav">
        {navItems.map((item) => {
          const Icon = navIcons[item.label];
          const to = item.value ? `/dispatch/${item.value}` : "/dispatch";
          const end = item.value === "";
          return (
            <NavLink
              key={item.label}
              className={({ isActive }) => `dispatch-subnav__item${isActive ? " is-active" : ""}`}
              end={end}
              to={to}
            >
              {Icon && <Icon size={16} />}
              {item.label}
            </NavLink>
          );
        })}
      </div>

      <Outlet />
    </div>
  );
}

export function DispatchCenterLayout() {
  return (
    <DispatchProvider>
      <DispatchCenterInner />
    </DispatchProvider>
  );
}
