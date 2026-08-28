import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { fetchDashboardOverview } from "../../shared/api/http";
import type { DashboardOverviewResponse } from "../../shared/api/types";
import {
  buildDashboardFlowSteps,
  buildDashboardOrderTrendSummary,
  normalizeDashboardOverview
} from "./dashboardPage.helpers";
import { useAdminRealtime } from "../../shared/realtime/adminRealtime";
import { AsyncContentView, type AsyncContentViewStatus } from "../../shared/components/AsyncContentView";
import { Clock, Truck, PackageCheck, ClipboardCheck } from "lucide-react";

// 看板数据相关的实时事件前缀。
// 骑手派单/送达/回执/异常、后台手动派单、以及订单增删改都会触发这些事件，
// 订阅后即可在事件到来时立刻重拉看板，保证与订单中心、骑手中心始终一致。
const DASHBOARD_EVENT_PREFIXES = ["dispatch.", "customer.order", "customer.wallet"];
// WebSocket 断连时仍能保持同步的兜底轮询间隔（秒）
const DASHBOARD_POLLING_MS = 15000;

function buildLinePath(values: number[], width: number, height: number, paddingX: number, paddingTop: number, paddingBottom: number) {
  if (!values.length) {
    return "";
  }
  const chartHeight = height - paddingTop - paddingBottom;
  const stepX = values.length === 1 ? 0 : (width - paddingX * 2) / (values.length - 1);
  const maxValue = Math.max(...values, 1);
  return values
    .map((value, index) => {
      const x = paddingX + stepX * index;
      const y = paddingTop + chartHeight - (value / maxValue) * chartHeight;
      return `${index === 0 ? "M" : "L"} ${x} ${y}`;
    })
    .join(" ");
}

function buildAreaPath(values: number[], width: number, height: number, paddingX: number, paddingTop: number, paddingBottom: number) {
  if (!values.length) {
    return "";
  }
  const linePath = buildLinePath(values, width, height, paddingX, paddingTop, paddingBottom);
  const stepX = values.length === 1 ? 0 : (width - paddingX * 2) / (values.length - 1);
  const startX = paddingX;
  const endX = paddingX + stepX * (values.length - 1);
  const baseY = height - paddingBottom;
  return `${linePath} L ${endX} ${baseY} L ${startX} ${baseY} Z`;
}

const FLOW_ICONS = {
  PENDING: Clock,
  DISPATCH_PENDING: ClipboardCheck,
  DISPATCHING: Truck,
  DELIVERED: PackageCheck
} as const;

// 按流转步骤序号映射图标（2 待处理 / 3 待派单 / 4 配送中 / 5 已送达）
const FLOW_ICONS_BY_STEP: Record<number, (typeof FLOW_ICONS)[keyof typeof FLOW_ICONS]> = {
  2: FLOW_ICONS.PENDING,
  3: FLOW_ICONS.DISPATCH_PENDING,
  4: FLOW_ICONS.DISPATCHING,
  5: FLOW_ICONS.DELIVERED
};

const FLOW_TONE: Record<string, { bar: string; num: string; iconBg: string }> = {
  neutral: { bar: "flow-step__bar-fill--neutral", num: "stat-val--neutral", iconBg: "flow-step__icon-bg--neutral" },
  blue:    { bar: "flow-step__bar-fill--blue",    num: "stat-val--blue",    iconBg: "flow-step__icon-bg--blue" },
  emerald: { bar: "flow-step__bar-fill--emerald", num: "stat-val--emerald", iconBg: "flow-step__icon-bg--emerald" }
};

export function DashboardPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [data, setData] = useState<DashboardOverviewResponse>(normalizeDashboardOverview({}));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const mountedRef = useRef(true);
  const activeReloadRef = useRef<Promise<void>>(Promise.resolve());
  const queuedReloadRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  // 可复用加载函数：静默刷新（不闪 loading），用于实时事件 / 轮询
  const loadOverview = useCallback(({ silent = false }: { silent?: boolean } = {}) => {
    activeReloadRef.current = activeReloadRef.current
      .catch(() => undefined)
      .then(async () => {
        if (!silent && mountedRef.current) {
          setLoading(true);
        }
        try {
          const response = await fetchDashboardOverview();
          if (!mountedRef.current) {
            return;
          }
          setData(normalizeDashboardOverview(response));
          setError(null);
        } catch (err) {
          if (mountedRef.current && !silent) {
            const e = err as { response?: { data?: { message?: string } }; message?: string };
            const msg: string =
              e?.response?.data?.message ||
              e?.message ||
              String(err);
            setError(msg);
          }
        } finally {
          if (mountedRef.current) {
            setLoading(false);
          }
        }
      });
    return activeReloadRef.current;
  }, []);

  // 首次加载 / 每次路由进入看板页时强制重新拉取最新数据
  // （从骑手中心 / 订单中心等页面跳转进来，保证看到的一定是最新数据，而不是缓存的旧值）
  useEffect(() => {
    void loadOverview().catch(() => undefined);
  }, [loadOverview, location.key]);

  // 实时订阅：派单 / 送达 / 回执 / 异常 / 订单增删改 → 立刻静默重拉看板
  useEffect(() => {
    return useAdminRealtime((message) => {
      const eventType = message?.eventType || message?.type || "";
      const isDashboardEvent = DASHBOARD_EVENT_PREFIXES.some((prefix) => eventType.startsWith(prefix));
      if (!isDashboardEvent) {
        return;
      }
      if (queuedReloadRef.current) {
        return;
      }
      queuedReloadRef.current = true;
      void loadOverview({ silent: true })
        .catch(() => undefined)
        .finally(() => {
          queuedReloadRef.current = false;
        });
    });
  }, [loadOverview]);

  // 轮询兜底：即使 WebSocket 断连，数字也会定期刷新，避免长期失步
  useEffect(() => {
    const timer = window.setInterval(() => {
      void loadOverview({ silent: true }).catch(() => undefined);
    }, DASHBOARD_POLLING_MS);
    return () => window.clearInterval(timer);
  }, [loadOverview]);

  const flowSteps = useMemo(() => buildDashboardFlowSteps(data), [data]);
  const orderTrend = Array.isArray(data.orderTrend) ? data.orderTrend : [];
  const orderSummary = useMemo(() => buildDashboardOrderTrendSummary(data), [data]);

  const status: AsyncContentViewStatus = loading
    ? "loading"
    : error
      ? "error"
      : "success";

  if (status !== "success") {
    return (
      <div className="admin-stack">
        <AsyncContentView
          status={status}
          error={error ?? undefined}
          onRetry={() => {
            setError(null);
            void loadOverview().catch(() => undefined);
          }}
        />
      </div>
    );
  }

  const todayTotal = data.todayServeMealCount;
  const todayLunch = data.todayServeLunchCount;
  const todayDinner = data.todayServeDinnerCount;

  // 订单趋势图几何参数
  const W = 760;
  const H = 220;
  const PAD_X = 72;
  const PAD_TOP = 36;
  const PAD_BOTTOM = 44;
  const orderValues = orderTrend.map((item) => item.total);
  const lunchValues = orderTrend.map((item) => item.lunch);
  const dinnerValues = orderTrend.map((item) => item.dinner);
  const orderMax = Math.max(...orderValues, 1);
  const orderLinePath = buildLinePath(orderValues, W, H, PAD_X, PAD_TOP, PAD_BOTTOM);
  const orderAreaPath = buildAreaPath(orderValues, W, H, PAD_X, PAD_TOP, PAD_BOTTOM);
  const lunchLinePath = buildLinePath(lunchValues, W, H, PAD_X, PAD_TOP, PAD_BOTTOM);
  const dinnerLinePath = buildLinePath(dinnerValues, W, H, PAD_X, PAD_TOP, PAD_BOTTOM);
  const orderPeakIndex = orderValues.findIndex((value) => value === orderSummary.peakValue);
  const orderStepX = orderTrend.length === 1 ? 0 : (W - PAD_X * 2) / Math.max(orderTrend.length - 1, 1);
  const orderPeakX = orderPeakIndex < 0 ? PAD_X : PAD_X + orderPeakIndex * orderStepX;
  const orderPeakY = PAD_TOP + ((H - PAD_TOP - PAD_BOTTOM) - (orderSummary.peakValue / orderMax) * (H - PAD_TOP - PAD_BOTTOM));
  const axisValue = (tick: number) => PAD_BOTTOM + ((H - PAD_TOP - PAD_BOTTOM) / 3) * tick;

  return (
    <div className="dashboard-bi">
      <div className="page-header dashboard-bi__header">
        <h2 className="page-title">经营看板</h2>
      </div>

      {/* ========== 今日 · 午餐 晚餐 分卡（stat-card 蓝色边框白色风格） ========== */}
      <section className="dashboard-bi__today-meal-row stat-row stat-row--double">
        <div
          className="stat-card stat-card--meal"
          role="button"
          tabIndex={0}
          onClick={() => navigate("/orders")}
          onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); navigate("/orders"); } }}
        >
          <div className="stat-title">
            今日午餐
            <span className="stat-meal-badge stat-meal-badge--lunch">LUNCH</span>
          </div>
          <div className="stat-val stat-val--blue">{todayLunch}</div>
          <div className="stat-footer">份 · 与订单中心同口径</div>
        </div>

        <div
          className="stat-card stat-card--meal"
          role="button"
          tabIndex={0}
          onClick={() => navigate("/orders")}
          onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); navigate("/orders"); } }}
        >
          <div className="stat-title">
            今日晚餐
            <span className="stat-meal-badge stat-meal-badge--dinner">DINNER</span>
          </div>
          <div className="stat-val stat-val--blue">{todayDinner}</div>
          <div className="stat-footer">份 · 与订单中心同口径</div>
        </div>
      </section>

      {/* ========== 今日 · 流转（4 张 stat-card 撑满） ========== */}
      <section className="stat-row dashboard-bi__flow-row">
        {flowSteps.map((step) => {
          const tone = FLOW_TONE[step.tone];
          const Icon = FLOW_ICONS_BY_STEP[step.index] ?? FLOW_ICONS.PENDING;
          return (
            <div
              key={step.label}
              className="stat-card stat-card--flow"
              role="button"
              tabIndex={0}
              onClick={() => step.path && navigate(step.path)}
              onKeyDown={(e) => {
                if ((e.key === "Enter" || e.key === " ") && step.path) {
                  e.preventDefault();
                  navigate(step.path);
                }
              }}
            >
              <div className="stat-title flow-step__title-row">
                <span className={`flow-step__index flow-step__index--${step.tone}`}>{step.index}</span>
                <span className="flow-step__name">{step.label}</span>
                <span className={`flow-step__icon-bg ${tone.iconBg}`} style={{ marginLeft: "auto" }}>
                  <Icon size={14} />
                </span>
              </div>
              <div className="stat-val-row">
                <span className={`stat-val ${tone.num}`}>{step.value}</span>
                <span className="stat-unit">份</span>
              </div>
              <div className="flow-step__bar">
                <span
                  className={`flow-step__bar-fill ${tone.bar}`}
                  style={{
                    width: step.value > 0
                      ? `${Math.min(100, Math.round((step.value / Math.max(todayTotal, 1)) * 100))}%`
                      : "0%"
                  }}
                />
              </div>
              <div className="stat-footer">{step.detail}</div>
            </div>
          );
        })}
      </section>

      {/* ========== 趋势 · 近 7 天（午餐晚餐分开） ========== */}
      <section className="dashboard-bi__panel dashboard-bi__panel--chart">
        <div className="dashboard-bi__panel-header">
          <div>
            <div className="dashboard-bi__eyebrow">近 7 天</div>
            <h3 className="dashboard-bi__panel-title">订单趋势</h3>
          </div>
          <button className="dashboard-bi__panel-link" onClick={() => navigate("/analysis")}>
            查看完整分析
          </button>
        </div>

        <div className="dashboard-bi__legend">
          <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--blue" />全部</span>
          <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--emerald" />午餐</span>
          <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--violet" />晚餐</span>
        </div>

        <div className="dashboard-bi__chart-wrap">
          <svg className="dashboard-bi__chart-svg dashboard-bi__chart-svg--compact" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="订单趋势图">
            <defs>
              <linearGradient id="dashboardOrderArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="rgba(37, 99, 235, 0.18)" />
                <stop offset="100%" stopColor="rgba(37, 99, 235, 0.02)" />
              </linearGradient>
            </defs>

            {[1, 2].map((tick) => {
              const y = PAD_TOP + ((H - PAD_TOP - PAD_BOTTOM) / 3) * tick;
              return <line key={tick} x1={PAD_X} y1={y} x2={W - PAD_X} y2={y} stroke="rgba(0, 0, 0, 0.04)" strokeDasharray="4 8" />;
            })}
            <line x1={PAD_X} y1={PAD_TOP} x2={PAD_X} y2={H - PAD_BOTTOM} stroke="rgba(0, 0, 0, 0.06)" />
            <line x1={PAD_X} y1={H - PAD_BOTTOM} x2={W - PAD_X} y2={H - PAD_BOTTOM} stroke="rgba(0, 0, 0, 0.06)" />

            <path d={orderAreaPath} fill="url(#dashboardOrderArea)" />
            <path d={orderLinePath} stroke="#2563eb" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            <path d={lunchLinePath} stroke="#10b981" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            <path d={dinnerLinePath} stroke="#7c3aed" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />

            {orderPeakIndex >= 0 ? (
              <>
                <circle cx={orderPeakX} cy={orderPeakY} r="3" fill="rgba(239,68,68,0.18)" />
                <circle cx={orderPeakX} cy={orderPeakY} r="1.6" fill="#ef4444" />
                <text x={orderPeakX + 8} y={Math.max(orderPeakY - 8, 24)} className="dashboard-bi__axis">
                  {`高峰 ${orderSummary.peakValue}`}
                </text>
              </>
            ) : null}

            {[0, Math.round(orderMax / 3), Math.round((orderMax * 2) / 3), orderMax].map((tick, index) => (
              <text key={index} x="20" y={axisValue(index) + 4} className="dashboard-bi__axis">{tick}</text>
            ))}

            {orderTrend.map((item, index) => (
              <text key={item.label} x={PAD_X + orderStepX * index} y={H - PAD_BOTTOM + 24} textAnchor="middle" className="dashboard-bi__axis">
                {item.label}
              </text>
            ))}
          </svg>
        </div>

        <div className="dashboard-bi__summary-grid dashboard-bi__summary-grid--four">
          <div className="dashboard-bi__summary-card">
            <div className="dashboard-bi__summary-label">7 日累计</div>
            <div className="dashboard-bi__summary-value">{orderSummary.sum}</div>
            <div className="dashboard-bi__summary-note">份</div>
          </div>
          <div className="dashboard-bi__summary-card">
            <div className="dashboard-bi__summary-label">日均</div>
            <div className="dashboard-bi__summary-value">{orderSummary.averageValue}</div>
            <div className="dashboard-bi__summary-note">份 / 天</div>
          </div>
          <div className="dashboard-bi__summary-card">
            <div className="dashboard-bi__summary-label">午餐占比</div>
            <div className="dashboard-bi__summary-value dashboard-bi__value--emerald">{orderSummary.lunchShare}%</div>
            <div className="dashboard-bi__summary-note">近 7 天</div>
          </div>
          <div className="dashboard-bi__summary-card">
            <div className="dashboard-bi__summary-label">区间</div>
            <div className="dashboard-bi__summary-value dashboard-bi__value--amber">{orderSummary.rangeText}</div>
            <div className="dashboard-bi__summary-note">最少 ~ 最多</div>
          </div>
        </div>
      </section>
    </div>
  );
}
