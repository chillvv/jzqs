import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchDashboardOverview } from "../../shared/api/http";
import type { DashboardOverviewResponse } from "../../shared/api/types";
import {
  buildDashboardHeroMetrics,
  buildDashboardOrderTrendSummary,
  buildDashboardProgressItems,
  buildDashboardTomorrowSummary,
  buildDashboardTodoItems,
  normalizeDashboardOverview,
  type DashboardActionKey
} from "./dashboardPage.helpers";
import { LowBalanceAlertModal } from "./LowBalanceAlertModal";
import { AsyncContentView, type AsyncContentViewStatus } from "../../shared/components/AsyncContentView";

const TONE_CLASS_MAP: Record<string, string> = {
  blue: "dashboard-bi__value--blue",
  cyan: "dashboard-bi__value--cyan",
  emerald: "dashboard-bi__value--emerald",
  violet: "dashboard-bi__value--violet",
  amber: "dashboard-bi__value--amber",
  red: "dashboard-bi__value--red"
};

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

function buildBarMetrics(values: number[], height: number, paddingTop: number, paddingBottom: number) {
  const chartHeight = height - paddingTop - paddingBottom;
  const maxValue = Math.max(...values, 1);
  return values.map((value) => ({
    height: Math.max((value / maxValue) * chartHeight, value > 0 ? 8 : 0),
    maxValue
  }));
}

type QuickLink = {
  key: string;
  title: string;
  desc: string;
  path: string;
};

const QUICK_LINKS: QuickLink[] = [
  { key: "orders", title: "订单助手", desc: "处理今日订单", path: "/orders" },
  { key: "dispatch", title: "调度中心", desc: "派单与配送", path: "/dispatch" },
  { key: "customers", title: "客户经营", desc: "续卡/余额提醒", path: "/customers" },
  { key: "aftersales", title: "售后中心", desc: "处理售后工单", path: "/aftersales" },
  { key: "menu", title: "菜单配置", desc: "未来菜单安排", path: "/menu" }
];

export function DashboardPage() {
  const navigate = useNavigate();
  const [data, setData] = useState<DashboardOverviewResponse>(normalizeDashboardOverview({}));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lowBalanceModalVisible, setLowBalanceModalVisible] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchDashboardOverview()
      .then((response) => {
        if (cancelled) {
          return;
        }
        setData(normalizeDashboardOverview(response));
        setError(null);
      })
      .catch((err) => {
        if (cancelled) {
          return;
        }
        setError(err?.response?.data?.message || err?.message || String(err));
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const heroMetrics = useMemo(() => buildDashboardHeroMetrics(data), [data]);
  const progressItems = useMemo(() => buildDashboardProgressItems(data), [data]);
  const todoItems = useMemo(() => buildDashboardTodoItems(data), [data]);
  const tomorrowSummary = useMemo(() => buildDashboardTomorrowSummary(data), [data]);
  const orderTrend = Array.isArray(data.orderTrend) ? data.orderTrend : [];
  const growthTrend = Array.isArray(data.growthTrend) ? data.growthTrend : [];
  const orderSummary = useMemo(() => buildDashboardOrderTrendSummary(data), [data]);

  const dashboardStatus: AsyncContentViewStatus = loading
    ? "loading"
    : error
      ? "error"
      : "success";

  if (dashboardStatus !== "success") {
    return (
      <div className="admin-stack">
        <AsyncContentView
          status={dashboardStatus}
          error={error ?? undefined}
          onRetry={() => {
            setError(null);
            window.location.reload();
          }}
        />
      </div>
    );
  }

  const orderValues = orderTrend.map((item) => item.total);
  const lunchValues = orderTrend.map((item) => item.lunch);
  const dinnerValues = orderTrend.map((item) => item.dinner);
  const orderMax = Math.max(...orderValues, 1);
  const orderLinePath = buildLinePath(orderValues, 760, 220, 72, 40, 48);
  const orderAreaPath = buildAreaPath(orderValues, 760, 220, 72, 40, 48);
  const lunchLinePath = buildLinePath(lunchValues, 760, 220, 72, 40, 48);
  const dinnerLinePath = buildLinePath(dinnerValues, 760, 220, 72, 40, 48);
  const orderPeakIndex = orderValues.findIndex((value) => value === orderSummary.peakValue);
  const orderStepX = orderTrend.length === 1 ? 0 : (760 - 72 * 2) / Math.max(orderTrend.length - 1, 1);
  const orderPeakX = orderPeakIndex < 0 ? 72 : 72 + orderPeakIndex * orderStepX;
  const orderPeakY = 40 + (132 - (orderSummary.peakValue / orderMax) * 132);

  const growthNewCards = growthTrend.map((item) => item.newCards);
  const growthRecharges = growthTrend.map((item) => item.recharges);
  const growthBarMetrics = buildBarMetrics([...growthNewCards, ...growthRecharges], 220, 28, 34);
  const growthMax = Math.max(...growthNewCards, ...growthRecharges, 1);
  const growthPeaks = {
    newCards: Math.max(...growthNewCards, 0),
    recharges: Math.max(...growthRecharges, 0)
  };
  const totalServe = data.todayServeMealCount;
  const progressFlowTotal = Math.max(totalServe, 1);
  const completionRate = totalServe > 0 ? Math.round((data.deliveredOrdersToday / totalServe) * 100) : 0;

  return (
    <div className="dashboard-bi">
      <div className="page-header dashboard-bi__header">
        <div>
          <h2 className="page-title">经营看板</h2>
          <p className="page-subtitle">聚焦今日与明日，所有指标都标注统计口径。</p>
        </div>
        <div className="dashboard-bi__header-actions">
          <div className="dashboard-bi__quick-nav" role="navigation" aria-label="快捷入口">
            {QUICK_LINKS.map((link) => (
              <button
                key={link.key}
                type="button"
                className="dashboard-bi__quick-nav-btn"
                onClick={() => navigate(link.path)}
              >
                <span className="dashboard-bi__quick-nav-title">{link.title}</span>
                <span className="dashboard-bi__quick-nav-desc">{link.desc}</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="dashboard-bi__metrics">
        {heroMetrics.map((item) => (
          <div
            key={item.label}
            className="dashboard-bi__metric-card dashboard-bi__metric-card--clickable"
            onClick={() => item.path && navigate(item.path)}
            role="button"
            tabIndex={0}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                if (item.path) {
                  navigate(item.path);
                }
              }
            }}
          >
            <div className="dashboard-bi__metric-label">{item.label}</div>
            <div className={`dashboard-bi__metric-value ${TONE_CLASS_MAP[item.tone] ?? ""}`}>
              {item.value}
              <span>{item.unit}</span>
            </div>
            <div className="dashboard-bi__metric-detail">{item.detail}</div>
            <div className="dashboard-bi__metric-scope">口径 · 按出餐日统计</div>
          </div>
        ))}
      </div>

      <div className="dashboard-bi__layout">
        <div className="dashboard-bi__main">
          <section className="table-container dashboard-bi__panel">
            <div className="dashboard-bi__panel-header">
              <div>
                <div className="dashboard-bi__eyebrow">今日 · 流转</div>
                <h3 className="dashboard-bi__panel-title">今日订单流转</h3>
                <p className="dashboard-bi__panel-desc">
                  按出餐日统计，每一份订单走到哪一步清晰可见。
                </p>
              </div>
              <button className="dashboard-bi__panel-link" onClick={() => navigate("/orders")}>
                进入订单中心
              </button>
            </div>

            <div className="dashboard-bi__flow">
              {progressItems.map((item, index) => {
                const ratio = Math.min(100, Math.round((item.value / progressFlowTotal) * 100));
                return (
                  <button
                    type="button"
                    key={item.label}
                    className="dashboard-bi__flow-step"
                    onClick={() => navigate(item.path)}
                  >
                    <div className="dashboard-bi__flow-step-head">
                      <span className="dashboard-bi__flow-step-index">{index + 1}</span>
                      <span className="dashboard-bi__flow-step-name">{item.label}</span>
                    </div>
                    <div className={`dashboard-bi__flow-step-value ${TONE_CLASS_MAP[item.tone] ?? ""}`}>
                      {item.value}
                      <span>份</span>
                    </div>
                    <div className="dashboard-bi__flow-step-bar">
                      <span
                        className={`dashboard-bi__flow-step-bar-fill dashboard-bi__value--${item.tone}`}
                        style={{ width: `${Math.max(ratio, item.value > 0 ? 12 : 0)}%` }}
                      />
                    </div>
                    <div className="dashboard-bi__flow-step-detail">{item.detail}</div>
                  </button>
                );
              })}
            </div>

            <div className="dashboard-bi__flow-legend">
              <span>
                完成率
                <strong>{completionRate}%</strong>
              </span>
              <span>
                待处理
                <strong>{data.pendingOrdersToday}</strong> 份
              </span>
              <span>
                今日取消
                <strong>{data.cancellationsToday}</strong> 份
              </span>
            </div>
          </section>
        </div>

        <aside className="dashboard-bi__side">
          <section className="table-container dashboard-bi__panel dashboard-bi__panel--highlight">
            <div className="dashboard-bi__panel-header">
              <div>
                <div className="dashboard-bi__eyebrow">明日 · 备餐预览</div>
                <h3 className="dashboard-bi__panel-title">明日要备多少餐？</h3>
                <p className="dashboard-bi__panel-desc">覆盖客户、固定订餐一目了然。</p>
              </div>
              <button className="dashboard-bi__panel-link" onClick={() => navigate("/orders")}>
                进入备餐管理
              </button>
            </div>

            <div className="dashboard-bi__tomorrow-summary">
              <div className="dashboard-bi__tomorrow-big">
                <span className="dashboard-bi__tomorrow-big-value">{tomorrowSummary.meals}</span>
                <span className="dashboard-bi__tomorrow-big-unit">份待备</span>
              </div>
              <div className="dashboard-bi__tomorrow-customer">
                覆盖 <strong>{tomorrowSummary.customers}</strong> 位客户 · 固定订餐 {tomorrowSummary.fixed} 位
                （{tomorrowSummary.fixedShare}%）
              </div>
            </div>

            <div className="dashboard-bi__tomorrow-meals">
              <button
                className="dashboard-bi__tomorrow-meal dashboard-bi__tomorrow-meal--lunch"
                onClick={() => navigate("/orders")}
              >
                <div className="dashboard-bi__tomorrow-meal-period">午餐</div>
                <div className="dashboard-bi__tomorrow-meal-value">{tomorrowSummary.lunches}</div>
                <div className="dashboard-bi__tomorrow-meal-share">
                  占比 {tomorrowSummary.lunchShare}%
                </div>
              </button>
              <button
                className="dashboard-bi__tomorrow-meal dashboard-bi__tomorrow-meal--dinner"
                onClick={() => navigate("/orders")}
              >
                <div className="dashboard-bi__tomorrow-meal-period">晚餐</div>
                <div className="dashboard-bi__tomorrow-meal-value">{tomorrowSummary.dinners}</div>
                <div className="dashboard-bi__tomorrow-meal-share">
                  占比 {tomorrowSummary.dinnerShare}%
                </div>
              </button>
            </div>

            <div className="dashboard-bi__tomorrow-fixed">
              <div className="dashboard-bi__tomorrow-fixed-bar">
                <span
                  className="dashboard-bi__tomorrow-fixed-bar-fill"
                  style={{ width: `${tomorrowSummary.fixedShare}%` }}
                />
              </div>
              <div className="dashboard-bi__tomorrow-fixed-note">
                固定订餐占比 {tomorrowSummary.fixedShare}%，剩余 {tomorrowSummary.customers - tomorrowSummary.fixed} 位客户等待手动下单
              </div>
            </div>
          </section>
        </aside>
      </div>

      <div className="dashboard-bi__layout dashboard-bi__layout--equal">
        <section className="table-container dashboard-bi__panel">
          <div className="dashboard-bi__panel-header">
            <div>
              <div className="dashboard-bi__eyebrow">待办与提醒</div>
              <h3 className="dashboard-bi__panel-title">商家待办</h3>
              <p className="dashboard-bi__panel-desc">点击进入对应模块处理。</p>
            </div>
          </div>

          <div className="dashboard-bi__todo-list">
            {todoItems.map((item) => (
              <button
                key={item.label}
                type="button"
                className="dashboard-bi__todo-item"
                onClick={() => {
                  if (item.key === ("低余额客户" as DashboardActionKey)) {
                    setLowBalanceModalVisible(true);
                    return;
                  }
                  navigate(item.path);
                }}
              >
                <span className={`dashboard-bi__todo-bullet dashboard-bi__value--${item.tone}`}>•</span>
                <span className="dashboard-bi__todo-content">
                  <span className="dashboard-bi__todo-label">{item.label}</span>
                  <span className="dashboard-bi__todo-detail">{item.detail}</span>
                </span>
                <span className={`dashboard-bi__todo-value ${TONE_CLASS_MAP[item.tone] ?? ""}`}>
                  {item.value}
                </span>
              </button>
            ))}
          </div>
        </section>

        <section className="table-container dashboard-bi__panel dashboard-bi__panel--chart">
          <div className="dashboard-bi__panel-header">
            <div>
              <div className="dashboard-bi__eyebrow">趋势 · 近 7 天</div>
              <h3 className="dashboard-bi__panel-title">订单趋势</h3>
              <p className="dashboard-bi__panel-desc">按出餐日期统计，已剔除取消/退款。</p>
            </div>
            <button className="dashboard-bi__panel-link" onClick={() => navigate("/analysis")}>
              查看分析详情
            </button>
          </div>

          <div className="dashboard-bi__legend">
            <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--blue" />订单份数</span>
            <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--emerald" />午餐</span>
            <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--violet" />晚餐</span>
            <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--red" />峰值标记</span>
          </div>

          <div className="dashboard-bi__chart-wrap">
            <svg className="dashboard-bi__chart-svg dashboard-bi__chart-svg--compact" viewBox="0 0 760 220" role="img" aria-label="订单趋势图">
              <defs>
                <linearGradient id="dashboardOrderArea" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="rgba(37, 99, 235, 0.26)" />
                  <stop offset="100%" stopColor="rgba(37, 99, 235, 0.02)" />
                </linearGradient>
              </defs>

              {[1, 2, 3].map((tick) => {
                const y = 40 + (132 / 4) * tick;
                return <line key={tick} x1="72" y1={y} x2="688" y2={y} stroke="rgba(0, 0, 0, 0.03)" strokeDasharray="4 8" />;
              })}
              <line x1="72" y1="40" x2="72" y2="172" stroke="rgba(0, 0, 0, 0.03)" />
              <line x1="72" y1="172" x2="688" y2="172" stroke="rgba(0, 0, 0, 0.03)" />

              <path d={orderAreaPath} fill="url(#dashboardOrderArea)" />
              <path d={orderLinePath} stroke="#2457f5" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
              <path d={lunchLinePath} stroke="#10b981" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
              <path d={dinnerLinePath} stroke="#7c3aed" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />

              <circle cx={orderPeakX} cy={orderPeakY} r="1.5" fill="#ef4444" />
              <circle cx={orderPeakX} cy={orderPeakY} r="3" fill="rgba(239,68,68,0.14)" />

              {[0, Math.round(orderMax / 3), Math.round((orderMax * 2) / 3), orderMax].map((tick, index) => {
                const y = 172 - (132 / 3) * index;
                return (
                  <text key={index} x="20" y={y + 4} className="dashboard-bi__axis">
                    {tick}
                  </text>
                );
              })}

              {orderTrend.map((item, index) => (
                <text
                  key={item.label}
                  x={72 + orderStepX * index}
                  y="196"
                  textAnchor="middle"
                  className="dashboard-bi__axis"
                >
                  {item.label}
                </text>
              ))}
              <text x={orderPeakX + 12} y={Math.max(orderPeakY - 12, 24)} className="dashboard-bi__axis">
                高峰 {orderSummary.peakValue}
              </text>
            </svg>
          </div>

          <div className="dashboard-bi__summary-grid dashboard-bi__summary-grid--four">
            <div className="dashboard-bi__summary-card">
              <div className="dashboard-bi__summary-label">近 7 天累计</div>
              <div className="dashboard-bi__summary-value">{orderSummary.sum}</div>
              <div className="dashboard-bi__summary-note">份</div>
            </div>
            <div className="dashboard-bi__summary-card">
              <div className="dashboard-bi__summary-label">日均</div>
              <div className="dashboard-bi__summary-value">{orderSummary.averageValue}</div>
              <div className="dashboard-bi__summary-note">份/天</div>
            </div>
            <div className="dashboard-bi__summary-card">
              <div className="dashboard-bi__summary-label">午餐占比</div>
              <div className="dashboard-bi__summary-value dashboard-bi__value--emerald">{orderSummary.lunchShare}%</div>
              <div className="dashboard-bi__summary-note">午餐/全单</div>
            </div>
            <div className="dashboard-bi__summary-card">
              <div className="dashboard-bi__summary-label">波动区间</div>
              <div className="dashboard-bi__summary-value dashboard-bi__value--amber">{orderSummary.rangeText}</div>
              <div className="dashboard-bi__summary-note">最少~最多</div>
            </div>
          </div>
        </section>
      </div>

      <section className="table-container dashboard-bi__panel dashboard-bi__panel--chart">
        <div className="dashboard-bi__panel-header">
          <div>
            <div className="dashboard-bi__eyebrow">增长 · 近 5 天</div>
            <h3 className="dashboard-bi__panel-title">新开卡与续卡</h3>
            <p className="dashboard-bi__panel-desc">跟踪新增客户与续卡充值餐数。</p>
          </div>
          <button className="dashboard-bi__panel-link" onClick={() => navigate("/customers")}>
            查看客户经营
          </button>
        </div>

        <div className="dashboard-bi__legend">
          <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--emerald" />新开卡</span>
          <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--violet" />续卡 / 充值</span>
        </div>

        <div className="dashboard-bi__chart-wrap">
          <svg className="dashboard-bi__chart-svg dashboard-bi__chart-svg--compact" viewBox="0 0 760 220" role="img" aria-label="开卡续卡趋势图">
            {[1, 2, 3].map((tick) => {
              const y = 28 + (154 / 4) * tick;
              return <line key={tick} x1="48" y1={y} x2="730" y2={y} stroke="rgba(0, 0, 0, 0.03)" strokeDasharray="4 8" />;
            })}
            <line x1="48" y1="28" x2="48" y2="184" stroke="rgba(0, 0, 0, 0.03)" />
            <line x1="48" y1="184" x2="730" y2="184" stroke="rgba(0, 0, 0, 0.03)" />

            {growthTrend.map((item, index) => {
              const groupX = 72 + index * 130;
              const greenBar = growthBarMetrics[index];
              const violetBar = growthBarMetrics[index + growthTrend.length];
              return (
                <g key={item.label}>
                  <rect x={groupX} y={184 - greenBar.height} width="32" height={greenBar.height} rx="9" fill="#10b981" />
                  <rect x={groupX + 40} y={184 - violetBar.height} width="32" height={violetBar.height} rx="9" fill="#7c3aed" />
                  <text x={groupX + 36} y="204" textAnchor="middle" className="dashboard-bi__axis">{item.label}</text>
                </g>
              );
            })}

            {[0, Math.round(growthMax / 3), Math.round((growthMax * 2) / 3), growthMax].map((tick, index) => {
              const y = 184 - (154 / 3) * index;
              return (
                <text key={index} x="14" y={y + 4} className="dashboard-bi__axis">
                  {tick}
                </text>
              );
            })}
          </svg>
        </div>

        <div className="dashboard-bi__summary-grid dashboard-bi__summary-grid--two">
          <div className="dashboard-bi__summary-card">
            <div className="dashboard-bi__summary-label">新开卡峰值</div>
            <div className="dashboard-bi__summary-value dashboard-bi__value--emerald">{growthPeaks.newCards}</div>
            <div className="dashboard-bi__summary-note">近 5 天单日最高</div>
          </div>
          <div className="dashboard-bi__summary-card">
            <div className="dashboard-bi__summary-label">续卡 / 充值峰值</div>
            <div className="dashboard-bi__summary-value dashboard-bi__value--violet">{growthPeaks.recharges}</div>
            <div className="dashboard-bi__summary-note">客户复购活跃度</div>
          </div>
        </div>
      </section>

      <LowBalanceAlertModal
        visible={lowBalanceModalVisible}
        onClose={() => setLowBalanceModalVisible(false)}
      />
    </div>
  );
}
