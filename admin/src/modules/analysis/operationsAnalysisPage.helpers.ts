import type { AnalysisOverviewResponse } from "../../shared/api/types";

function toNumber(value: number | string) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function formatMoney(value: number | string) {
  return toNumber(value).toFixed(2);
}

export function buildAnalysisInsights(overview: AnalysisOverviewResponse) {
  const totalSales = toNumber(overview.totalSales);
  const totalProfit = toNumber(overview.totalProfit);
  const totalOrders = Math.max(overview.totalOrders, 1);

  return {
    grossMarginRate: `${((totalProfit / Math.max(totalSales, 1)) * 100).toFixed(1)}%`,
    avgOrderValue: (totalSales / totalOrders).toFixed(1),
    aftersaleRate: `${((overview.aftersaleCount / totalOrders) * 100).toFixed(1)}%`
  };
}
