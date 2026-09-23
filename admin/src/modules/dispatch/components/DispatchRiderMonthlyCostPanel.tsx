import { useEffect, useState } from "react";
import { Wallet } from "lucide-react";
import { extractAdminApiErrorMessage, fetchDispatchRiderMonthlyStats } from "../../../shared/api/http";
import type { DispatchRiderMonthlyStatsResponse } from "../../../shared/api/types";
import { SafeInput } from "../../../shared/components/SafeInput";
import { toast } from "../../../shared/components/Toast";
import { PAGE_MEMORY_KEYS, usePersistedState } from "../../../shared/hooks/usePersistedState";
import { formatLocalMonthInputValue } from "../../../shared/utils/dateTime";
import {
  clampPercent,
  formatCostPerOrder,
  formatMoneyAmount,
  formatMonthlySalary,
  resolveRiderCostTone
} from "../dispatchCenterLayout.helpers";

/**
 * 骑手月度配送成本面板：按月看每个骑手送了多少单、占全部骑手单量的比例、以及单均人工成本。
 * 口径与后端一致：单量按已送达的餐段订单数计（一个订单 = 1 单，与份数无关）；
 * 单均成本 = 该骑手月薪 ÷ 该骑手当月单量，未设置月薪的骑手只给单量与占比。
 * 单均成本以「整体单均成本」为基准着色，一眼看出谁省谁贵。
 */
export function DispatchRiderMonthlyCostPanel({ refreshToken = 0 }: { refreshToken?: number }) {
  const [month, setMonth] = usePersistedState<string>(
    PAGE_MEMORY_KEYS.dispatchRidersStatsMonth,
    formatLocalMonthInputValue()
  );
  const [stats, setStats] = useState<DispatchRiderMonthlyStatsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const activeMonth = month || formatLocalMonthInputValue();

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchDispatchRiderMonthlyStats(activeMonth)
      .then((data) => {
        if (!cancelled) {
          setStats(data);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          toast(extractAdminApiErrorMessage(err, "加载骑手月度成本失败"), "error");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [activeMonth, refreshToken]);

  const riders = stats?.riders ?? [];
  const baseline = stats?.averageCostPerOrder ?? null;
  const salaryConfiguredCount = riders.filter((item) => item.monthlySalary != null && item.monthlySalary > 0).length;
  const costRanked = stats
    ? [...riders].filter((item) => item.costPerOrder != null).sort((a, b) => (b.costPerOrder ?? 0) - (a.costPerOrder ?? 0))
    : [];
  const mostExpensive = costRanked[0] ?? null;

  return (
    <section className="rider-cost-panel">
      <header className="rider-cost-panel__header">
        <div>
          <div className="rider-cost-panel__title">
            <Wallet size={16} />
            骑手月度配送成本
          </div>
          <div className="rider-cost-panel__subtitle">
            单量按已送达餐段订单计（一个订单 = 1 单）；单均成本 = 月薪 ÷ 当月单量，单量越少单均越高
          </div>
        </div>
        <div className="rider-cost-panel__toolbar">
          <span className="rider-cost-panel__count">{loading ? "加载中…" : `${riders.length} 名骑手`}</span>
          <SafeInput
            type="month"
            className="form-control"
            style={{ width: 152 }}
            value={month}
            onValueChange={(value) => setMonth(value)}
          />
        </div>
      </header>

      <div className="rider-cost-kpis">
        <div className="rider-cost-kpi">
          <div className="rider-cost-kpi__label">当月送达</div>
          <div className="rider-cost-kpi__value">
            {stats?.totalDeliveredCount ?? 0}
            <span className="rider-cost-kpi__unit">单</span>
          </div>
          <div className="rider-cost-kpi__hint">{activeMonth} 全部骑手合计</div>
        </div>
        <div className="rider-cost-kpi rider-cost-kpi--cost">
          <div className="rider-cost-kpi__label">当月人工成本</div>
          <div className="rider-cost-kpi__value">¥ {formatMoneyAmount(stats?.totalMonthlyCost)}</div>
          <div className="rider-cost-kpi__hint">{salaryConfiguredCount} 名骑手已设月薪</div>
        </div>
        <div className="rider-cost-kpi rider-cost-kpi--unit">
          <div className="rider-cost-kpi__label">整体单均成本</div>
          <div className="rider-cost-kpi__value">{formatCostPerOrder(baseline)}</div>
          <div className="rider-cost-kpi__hint">人工成本 ÷ 当月总单量</div>
        </div>
        <div className="rider-cost-kpi">
          <div className="rider-cost-kpi__label">最高单均成本</div>
          <div className="rider-cost-kpi__value">
            {mostExpensive ? formatCostPerOrder(mostExpensive.costPerOrder) : "--"}
          </div>
          <div className="rider-cost-kpi__hint">
            {mostExpensive ? `${mostExpensive.riderName} · ${mostExpensive.deliveredCount} 单` : "暂无成本数据"}
          </div>
        </div>
      </div>

      {riders.length > 0 && (
        <div className="rider-cost-list">
          <div className="rider-cost-list__head">
            <span>骑手</span>
            <span>月送达单量 / 占比</span>
            <span>月薪</span>
            <span>单均成本</span>
          </div>
          {riders.map((rider, index) => {
            const tone = resolveRiderCostTone(rider.costPerOrder, baseline);
            const idle = rider.deliveredCount === 0;
            // 有单量的骑手至少留 1.5% 的条宽，避免 0.x% 的占比条几乎看不见
            const fillWidth = idle ? 0 : Math.max(clampPercent(rider.sharePercent), 1.5);
            return (
              <div
                key={rider.riderId}
                className={`rider-cost-row${idle ? " rider-cost-row--idle" : ""}`}
              >
                <div className="rider-cost-row__rider">
                  <span
                    className={`rider-cost-row__rank${index < 3 && !idle ? " rider-cost-row__rank--top" : ""}`}
                  >
                    {index + 1}
                  </span>
                  <span className="rider-cost-row__name">{rider.riderName}</span>
                  {rider.areaCode && <span className="rider-cost-row__area">{rider.areaCode}</span>}
                </div>
                <div className="rider-cost-row__volume">
                  <div className="rider-cost-bar">
                    <div className="rider-cost-bar__fill" style={{ width: `${fillWidth}%` }} />
                  </div>
                  <div className="rider-cost-row__numbers">
                    <strong>{rider.deliveredCount}</strong> 单 · {rider.sharePercent}%
                  </div>
                </div>
                <div
                  className={`rider-cost-row__salary${rider.monthlySalary == null ? " rider-cost-row__salary--empty" : ""}`}
                >
                  {formatMonthlySalary(rider.monthlySalary)}
                </div>
                <div className="rider-cost-row__cost">
                  <span className={`rider-cost-pill rider-cost-pill--${tone}`}>
                    {tone === "none" ? "未设置" : formatCostPerOrder(rider.costPerOrder)}
                    {tone !== "none" && <span className="rider-cost-pill__unit">/单</span>}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {!loading && riders.length === 0 && (
        <div className="rider-cost-empty">该月暂无骑手配送数据</div>
      )}

      {mostExpensive && (
        <div className="rider-cost-legend">
          <span className="rider-cost-legend__item">
            <i className="rider-cost-legend__dot" style={{ background: "var(--success-color)" }} />
            低于整体均值 15%
          </span>
          <span className="rider-cost-legend__item">
            <i className="rider-cost-legend__dot" style={{ background: "var(--primary-color)" }} />
            接近整体均值
          </span>
          <span className="rider-cost-legend__item">
            <i className="rider-cost-legend__dot" style={{ background: "var(--error-500)" }} />
            高于整体均值 15%
          </span>
        </div>
      )}

      <div className="rider-cost-note">
        {stats && stats.unassignedDeliveredCount > 0
          ? `另有 ${stats.unassignedDeliveredCount} 单未归属到骑手档案（历史数据），未计入占比。`
          : "占比 = 该骑手当月单量 ÷ 当月全部已送达单量。"}
        {salaryConfiguredCount === 0 && !loading
          ? " 还没有骑手设置月薪，单均成本暂不可计算：在下方骑手列表点「编辑」补上即可。"
          : ""}
      </div>
    </section>
  );
}
