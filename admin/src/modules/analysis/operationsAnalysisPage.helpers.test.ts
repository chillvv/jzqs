import { describe, expect, it } from "vitest";
import { buildAnalysisInsights, formatMoney } from "./operationsAnalysisPage.helpers";
import type { AnalysisOverviewResponse } from "../../shared/api/types";

const overview: AnalysisOverviewResponse = {
  date: "2026-05-14",
  totalSales: 4280,
  totalCost: 2310,
  totalProfit: 1970,
  totalOrders: 96,
  totalMeals: 105,
  aftersaleCount: 4
};

describe("formatMoney", () => {
  it("formats numbers and numeric strings with two decimals", () => {
    expect(formatMoney(0)).toBe("0.00");
    expect(formatMoney(12.3)).toBe("12.30");
    expect(formatMoney("45.678")).toBe("45.68");
  });
});

describe("buildAnalysisInsights", () => {
  it("derives margin, average order value and aftersale rate from the overview", () => {
    expect(buildAnalysisInsights(overview)).toEqual({
      grossMarginRate: "46.0%",
      avgOrderValue: "44.6",
      aftersaleRate: "4.2%"
    });
  });
});
