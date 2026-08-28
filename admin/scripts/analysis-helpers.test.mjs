import assert from "node:assert/strict";
import {
  buildAnalysisInsights,
  formatMoney
} from "../temp-test/modules/analysis/operationsAnalysisPage.helpers.js";

const overview = {
  date: "2026-05-14",
  totalSales: 4280,
  totalCost: 2310,
  totalProfit: 1970,
  totalOrders: 96,
  totalMeals: 105,
  specialOrders: 12,
  aftersaleCount: 4
};

{
  const summary = buildAnalysisInsights(overview);
  assert.deepEqual(summary, {
    grossMarginRate: "46.0%",
    avgOrderValue: "44.6",
    specialOrderRate: "12.5%",
    aftersaleRate: "4.2%"
  });
}

assert.equal(formatMoney(0), "0.00");
assert.equal(formatMoney(12.3), "12.30");
assert.equal(formatMoney("45.678"), "45.68");

console.log("analysis helpers test: ok");
