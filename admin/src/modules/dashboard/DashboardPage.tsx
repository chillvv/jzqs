import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import * as echarts from "echarts/core";
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
import { EChart } from "../../shared/components/EChart";
import { Clock, Truck, PackageCheck, ClipboardCheck } from "lucide-react";

// 看板数据相关的实时事件前缀。
// 骑手派单/送达/回执/异常、后台手动派单、以及订单增删改都会触发这些事件，
// 订阅后即可在事件到来时立刻重拉看板，保证与订单中心、骑手中心始终一致。
const DASHBOARD_EVENT_PREFIXES = ["dispatch.", "customer.order", "customer.wallet"];
// WebSocket 断连时仍能保持同步的兜底轮询间隔（秒）
const DASHBOARD_POLLING_MS = 15000;

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

  // ===== 订单趋势图（ECharts）=====
  // Y 轴刻度方向由 ECharts 内部保证（值越大越靠上），不再手写坐标映射，杜绝刻度颠倒类 bug
  const orderValues = orderTrend.map((item) => item.total);
  const lunchValues = orderTrend.map((item) => item.lunch);
  const dinnerValues = orderTrend.map((item) => item.dinner);
  const chartOption = {
    color: ["#2563eb", "#10b981", "#7c3aed"],
    grid: { left: 52, right: 24, top: 36, bottom: 30 },
    legend: {
      top: 0,
      right: 4,
      icon: "circle",
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 18,
      textStyle: { color: "#64748b", fontSize: 12, fontWeight: 600 }
    },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "line" },
      backgroundColor: "rgba(255,255,255,0.96)",
      borderColor: "rgba(226,232,240,0.9)",
      textStyle: { color: "#334155", fontSize: 12 }
    },
    xAxis: {
      type: "category",
      boundaryGap: false,
      data: orderTrend.map((item) => item.label),
      axisLine: { lineStyle: { color: "rgba(0,0,0,0.08)" } },
      axisTick: { show: false },
      axisLabel: { color: "#94a3b8", fontSize: 11, fontWeight: 700 }
    },
    yAxis: {
      type: "value",
      min: 0,
      splitLine: { lineStyle: { color: "rgba(0,0,0,0.04)", type: "dashed" } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: "#94a3b8", fontSize: 11, fontWeight: 700 }
    },
    series: [
      {
        name: "全部",
        type: "line",
        data: orderValues,
        smooth: true,
        symbol: "circle",
        symbolSize: 5,
        lineStyle: { width: 2, color: "#2563eb" },
        itemStyle: { color: "#2563eb" },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: "rgba(37, 99, 235, 0.18)" },
            { offset: 1, color: "rgba(37, 99, 235, 0.02)" }
          ])
        },
        markPoint: {
          data: [{ type: "max", name: "高峰" }],
          symbol: "circle",
          symbolSize: 9,
          itemStyle: { color: "rgba(239,68,68,0.18)" },
          label: {
            show: true,
            color: "#ef4444",
            fontSize: 11,
            fontWeight: 700,
            position: "top",
            formatter: (params: unknown) => {
              const value = (params as { value?: number }).value ?? 0;
              return `高峰 ${value}`;
            }
          }
        }
      },
      {
        name: "午餐",
        type: "line",
        data: lunchValues,
        smooth: true,
        symbol: "circle",
        symbolSize: 4,
        lineStyle: { width: 1.5, color: "#10b981" },
        itemStyle: { color: "#10b981" }
      },
      {
        name: "晚餐",
        type: "line",
        data: dinnerValues,
        smooth: true,
        symbol: "circle",
        symbolSize: 4,
        lineStyle: { width: 1.5, color: "#7c3aed" },
        itemStyle: { color: "#7c3aed" }
      }
    ]
  };

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

        <div className="dashboard-bi__chart-wrap">
          <EChart option={chartOption} height={300} ariaLabel="近 7 天订单趋势图" />
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
