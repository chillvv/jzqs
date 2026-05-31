import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchDashboardOverview } from "../../shared/api/http";
import type { DashboardOverviewResponse } from "../../shared/api/types";
import {
  buildDashboardExceptionItems,
  buildDashboardHeroMetrics,
  buildDashboardOrderTrendSummary,
  buildDashboardProgressItems,
  normalizeDashboardOverview
} from "./dashboardPage.helpers";
import { LowBalanceAlertModal } from "./LowBalanceAlertModal";

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
  const exceptionItems = useMemo(() => buildDashboardExceptionItems(data), [data]);
  const orderSummary = useMemo(() => buildDashboardOrderTrendSummary(data), [data]);
  const orderTrend = Array.isArray(data.orderTrend) ? data.orderTrend : [];
  const growthTrend = Array.isArray(data.growthTrend) ? data.growthTrend : [];

  if (error) {
    return (
      <div className="admin-stack">
        <div className="dispatch-empty" style={{ color: "var(--error-color)" }}>
          数据加载失败：{error}
          <br />
          <button className="btn btn-outline" style={{ marginTop: "12px" }} onClick={() => { setError(null); window.location.reload(); }}>
            重试
          </button>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="admin-stack">
        <div className="dispatch-empty">加载中...</div>
      </div>
    );
  }

  const orderValues = orderTrend.map((item) => item.total);
  const lunchValues = orderTrend.map((item) => item.lunch);
  const dinnerValues = orderTrend.map((item) => item.dinner);
  const orderMax = Math.max(...orderValues, 1);
  const orderLinePath = buildLinePath(orderValues, 760, 280, 56, 26, 34);
  const orderAreaPath = buildAreaPath(orderValues, 760, 280, 56, 26, 34);
  const lunchLinePath = buildLinePath(lunchValues, 760, 280, 56, 26, 34);
  const dinnerLinePath = buildLinePath(dinnerValues, 760, 280, 56, 26, 34);
  const orderPeakIndex = orderValues.findIndex((value) => value === orderSummary.peakValue);
  const orderStepX = orderTrend.length === 1 ? 0 : (760 - 56 * 2) / Math.max(orderTrend.length - 1, 1);
  const orderPeakX = orderPeakIndex < 0 ? 56 : 56 + orderPeakIndex * orderStepX;
  const orderPeakY = 26 + (220 - (orderSummary.peakValue / orderMax) * 220);

  const growthNewCards = growthTrend.map((item) => item.newCards);
  const growthRecharges = growthTrend.map((item) => item.recharges);
  const growthBarMetrics = buildBarMetrics([...growthNewCards, ...growthRecharges], 280, 28, 34);
  const growthMax = Math.max(...growthNewCards, ...growthRecharges, 1);
  const growthPeaks = {
    newCards: Math.max(...growthNewCards, 0),
    recharges: Math.max(...growthRecharges, 0)
  };

  return (
    <div className="dashboard-bi">
      <div className="page-header dashboard-bi__header">
        <div>
          <h2 className="page-title">经营看板</h2>
          <p className="page-subtitle">经营总览、趋势与异常</p>
        </div>
        <div className="dashboard-bi__header-note">
          聚焦今日经营和近 7 天变化
        </div>
      </div>

      <div className="dashboard-bi__metrics">
        {heroMetrics.map((item) => (
          <div key={item.label} className="dashboard-bi__metric-card">
            <div className="dashboard-bi__metric-label">{item.label}</div>
            <div className={`dashboard-bi__metric-value ${TONE_CLASS_MAP[item.tone] ?? ""}`}>
              {item.value}
              <span>{item.unit}</span>
            </div>
            <div className="dashboard-bi__metric-detail">{item.detail}</div>
          </div>
        ))}
      </div>

      <div className="dashboard-bi__layout">
        <div className="dashboard-bi__main">
          <section className="table-container dashboard-bi__panel dashboard-bi__panel--chart">
            <div className="dashboard-bi__panel-header">
              <div>
                <div className="dashboard-bi__eyebrow">主图</div>
                <h3 className="dashboard-bi__panel-title">订单趋势</h3>
                <p className="dashboard-bi__panel-desc">查看近 7 天订单走势和午晚餐结构。</p>
              </div>
              <button className="dashboard-bi__panel-link" onClick={() => navigate("/analysis")}>
                查看趋势详情
              </button>
            </div>

            <div className="dashboard-bi__legend">
              <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--blue" />订单份数</span>
              <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--emerald" />午餐</span>
              <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--violet" />晚餐</span>
              <span><i className="dashboard-bi__legend-dot dashboard-bi__legend-dot--red" />峰值标记</span>
            </div>

            <div className="dashboard-bi__chart-wrap">
              <svg className="dashboard-bi__chart-svg" viewBox="0 0 760 280" role="img" aria-label="订单趋势图">
                <defs>
                  <linearGradient id="dashboardOrderArea" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="rgba(37, 99, 235, 0.26)" />
                    <stop offset="100%" stopColor="rgba(37, 99, 235, 0.02)" />
                  </linearGradient>
                </defs>

                {[1, 2, 3].map((tick) => {
                  const y = 26 + (220 / 4) * tick;
                  return <line key={tick} x1="56" y1={y} x2="704" y2={y} stroke="#e7eef8" strokeDasharray="4 8" />;
                })}
                <line x1="56" y1="26" x2="56" y2="246" stroke="#d7e0ed" />
                <line x1="56" y1="246" x2="704" y2="246" stroke="#d7e0ed" />

                <path d={orderAreaPath} fill="url(#dashboardOrderArea)" />
                <path d={orderLinePath} stroke="#2457f5" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" fill="none" />
                <path d={lunchLinePath} stroke="#10b981" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" fill="none" />
                <path d={dinnerLinePath} stroke="#7c3aed" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" fill="none" />

                <circle cx={orderPeakX} cy={orderPeakY} r="7" fill="#ef4444" />
                <circle cx={orderPeakX} cy={orderPeakY} r="13" fill="rgba(239,68,68,0.14)" />

                {[0, Math.round(orderMax / 3), Math.round((orderMax * 2) / 3), orderMax].map((tick, index) => {
                  const y = 246 - (220 / 3) * index;
                  return (
                    <text key={index} x="18" y={y + 4} className="dashboard-bi__axis">
                      {tick}
                    </text>
                  );
                })}

                {orderTrend.map((item, index) => (
                  <text
                    key={item.label}
                    x={56 + orderStepX * index}
                    y="270"
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

            <div className="dashboard-bi__summary-grid">
              <div className="dashboard-bi__summary-card">
                <div className="dashboard-bi__summary-label">近 7 天峰值</div>
                <div className="dashboard-bi__summary-value dashboard-bi__value--blue">{orderSummary.peakValue}</div>
                <div className="dashboard-bi__summary-note">峰值日期 {orderSummary.peakLabel}</div>
              </div>
              <div className="dashboard-bi__summary-card">
                <div className="dashboard-bi__summary-label">近 7 天均值</div>
                <div className="dashboard-bi__summary-value">{orderSummary.averageValue}</div>
                <div className="dashboard-bi__summary-note">日均订单水平</div>
              </div>
              <div className="dashboard-bi__summary-card">
                <div className="dashboard-bi__summary-label">午餐占比</div>
                <div className="dashboard-bi__summary-value dashboard-bi__value--emerald">{orderSummary.lunchShare}%</div>
                <div className="dashboard-bi__summary-note">午餐占总订单比重</div>
              </div>
              <div className="dashboard-bi__summary-card">
                <div className="dashboard-bi__summary-label">波动区间</div>
                <div className="dashboard-bi__summary-value dashboard-bi__value--amber">{orderSummary.rangeText}</div>
                <div className="dashboard-bi__summary-note">近 7 天最低到最高区间</div>
              </div>
            </div>
          </section>

          <div className="dashboard-bi__two-col">
            <section className="table-container dashboard-bi__panel">
              <div className="dashboard-bi__panel-header">
                <div>
                  <div className="dashboard-bi__eyebrow">流程面板</div>
                  <h3 className="dashboard-bi__panel-title">今日订单进度</h3>
                  <p className="dashboard-bi__panel-desc">查看今日订单流转节点。</p>
                </div>
              </div>

              <div className="dashboard-bi__list">
                {progressItems.map((item) => (
                  <div key={item.label} className="dashboard-bi__list-item">
                    <div>
                      <div className="dashboard-bi__list-name">{item.label}</div>
                      <div className="dashboard-bi__list-detail">{item.detail}</div>
                    </div>
                    <div className={`dashboard-bi__list-value ${TONE_CLASS_MAP[item.tone] ?? ""}`}>{item.value}</div>
                  </div>
                ))}
              </div>
            </section>

            <section className="table-container dashboard-bi__panel">
              <div className="dashboard-bi__panel-header">
                <div>
                  <div className="dashboard-bi__eyebrow">异常面板</div>
                  <h3 className="dashboard-bi__panel-title">今日异常关注</h3>
                  <p className="dashboard-bi__panel-desc">集中查看需优先处理的异常。</p>
                </div>
              </div>

              <div className="dashboard-bi__list">
                {exceptionItems.map((item) => (
                  (() => {
                    const clickable = item.label === "低余额客户";
                    return (
                  <div
                    key={item.label}
                    className={`dashboard-bi__list-item ${clickable ? "dashboard-bi__list-item--clickable" : ""}`}
                    onClick={() => {
                      if (item.label === "低余额客户") {
                        setLowBalanceModalVisible(true);
                      }
                    }}
                    style={clickable ? { cursor: "pointer" } : undefined}
                  >
                    <div>
                      <div className="dashboard-bi__list-name">{item.label}</div>
                      <div className="dashboard-bi__list-detail">{item.detail}</div>
                    </div>
                    <div className={`dashboard-bi__list-value ${TONE_CLASS_MAP[item.tone] ?? ""}`}>{item.value}</div>
                  </div>
                    );
                  })()
                ))}
              </div>
            </section>
          </div>
        </div>

        <aside className="dashboard-bi__side">
          <section className="table-container dashboard-bi__panel dashboard-bi__panel--chart">
            <div className="dashboard-bi__panel-header">
              <div>
                <div className="dashboard-bi__eyebrow">副图</div>
                <h3 className="dashboard-bi__panel-title">开卡与续卡</h3>
                <p className="dashboard-bi__panel-desc">查看近 7 天新增和续卡走势。</p>
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
              <svg className="dashboard-bi__chart-svg dashboard-bi__chart-svg--compact" viewBox="0 0 420 280" role="img" aria-label="开卡续卡趋势图">
                {[1, 2, 3].map((tick) => {
                  const y = 28 + (218 / 4) * tick;
                  return <line key={tick} x1="48" y1={y} x2="380" y2={y} stroke="#e7eef8" strokeDasharray="4 8" />;
                })}
                <line x1="48" y1="28" x2="48" y2="246" stroke="#d7e0ed" />
                <line x1="48" y1="246" x2="380" y2="246" stroke="#d7e0ed" />

                {growthTrend.map((item, index) => {
                  const groupX = 74 + index * 62;
                  const greenBar = growthBarMetrics[index];
                  const violetBar = growthBarMetrics[index + growthTrend.length];
                  return (
                    <g key={item.label}>
                      <rect x={groupX} y={246 - greenBar.height} width="18" height={greenBar.height} rx="9" fill="#10b981" />
                      <rect x={groupX + 24} y={246 - violetBar.height} width="18" height={violetBar.height} rx="9" fill="#7c3aed" />
                      <text x={groupX + 21} y="268" textAnchor="middle" className="dashboard-bi__axis">{item.label}</text>
                    </g>
                  );
                })}

                {[0, Math.round(growthMax / 3), Math.round((growthMax * 2) / 3), growthMax].map((tick, index) => {
                  const y = 246 - (218 / 3) * index;
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
                <div className="dashboard-bi__summary-note">近 7 天单日最高值</div>
              </div>
              <div className="dashboard-bi__summary-card">
                <div className="dashboard-bi__summary-label">续卡 / 充值峰值</div>
                <div className="dashboard-bi__summary-value dashboard-bi__value--violet">{growthPeaks.recharges}</div>
                <div className="dashboard-bi__summary-note">用于判断客户续卡活跃度</div>
              </div>
            </div>
          </section>

          <section className="table-container dashboard-bi__panel">
            <div className="dashboard-bi__panel-header">
              <div>
                <div className="dashboard-bi__eyebrow">快捷入口</div>
                <h3 className="dashboard-bi__panel-title">关键模块跳转</h3>
                <p className="dashboard-bi__panel-desc">从总览直接进入高频模块。</p>
              </div>
            </div>

            <div className="dashboard-bi__detail-list">
              {[
                {
                  title: "订单趋势",
                  desc: "查看多周期趋势、午晚餐结构和峰值订单。",
                  action: () => navigate("/analysis")
                },
                {
                  title: "客户经营",
                  desc: "查看开卡、续卡、低余额名单和客户档案。",
                  action: () => navigate("/customers")
                },
                {
                  title: "明日备餐",
                  desc: "查看明日订单量、午晚餐拆分和固定订餐占比。",
                  action: () => navigate("/orders")
                }
              ].map((item) => (
                <button key={item.title} className="dashboard-bi__detail-card" onClick={item.action}>
                  <div className="dashboard-bi__detail-title">{item.title}</div>
                  <div className="dashboard-bi__detail-desc">{item.desc}</div>
                  <div className="dashboard-bi__detail-link">进入模块</div>
                </button>
              ))}
            </div>
          </section>
        </aside>
      </div>

      <LowBalanceAlertModal
        visible={lowBalanceModalVisible}
        onClose={() => setLowBalanceModalVisible(false)}
      />
    </div>
  );
}
