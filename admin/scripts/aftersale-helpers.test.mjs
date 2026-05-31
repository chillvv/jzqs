import assert from "node:assert/strict";
import {
  buildAftersaleStats,
  buildAftersaleView,
  formatCompensationSummary,
  resolveAftersaleStatusTone
} from "../temp-test/modules/aftersales/aftersalePage.helpers.js";

const items = [
  {
    id: 1,
    issueType: "DELIVERY_EXCEPTION",
    issueDesc: "午餐撒漏",
    resolutionType: "ROLLBACK_AND_COMPENSATE",
    rollbackMeal: true,
    bonusMeals: 2,
    compensationItem: "果蔬汁",
    status: "OPEN",
    operatorName: "后台客服",
    customerName: "张先生",
    customerPhone: "13800000001"
  },
  {
    id: 2,
    issueType: "FOOD_SAFETY",
    issueDesc: "异物投诉",
    resolutionType: "ROLLBACK_ONLY",
    rollbackMeal: true,
    bonusMeals: 0,
    compensationItem: "",
    status: "RESOLVED",
    operatorName: "老板",
    customerName: "李女士",
    customerPhone: "13800000002"
  },
  {
    id: 3,
    issueType: "SERVICE",
    issueDesc: "配送员联系不上",
    resolutionType: "REGISTER_ONLY",
    rollbackMeal: false,
    bonusMeals: 0,
    compensationItem: "",
    status: "OPEN",
    operatorName: "后台客服",
    customerName: "王先生",
    customerPhone: "13800000003"
  }
];

assert.deepEqual(buildAftersaleStats(items), {
  totalCount: 3,
  openCount: 2,
  resolvedCount: 1,
  rollbackCount: 2,
  compensationCount: 1
});

{
  const view = buildAftersaleView(
    items,
    {
      keyword: "13800000001",
      status: "OPEN",
      resolutionType: "ROLLBACK_AND_COMPENSATE"
    },
    1,
    10
  );

  assert.equal(view.totalItems, 1);
  assert.equal(view.totalPages, 1);
  assert.deepEqual(view.pageItems.map((item) => item.id), [1]);
}

{
  const view = buildAftersaleView(
    items,
    {
      keyword: "",
      status: "ALL",
      resolutionType: "ALL"
    },
    2,
    2
  );

  assert.equal(view.totalItems, 3);
  assert.equal(view.totalPages, 2);
  assert.deepEqual(view.pageItems.map((item) => item.id), [3]);
}

assert.equal(formatCompensationSummary(items[0]), "撤回1餐 + 补2餐 + 果蔬汁");
assert.equal(formatCompensationSummary(items[1]), "撤回1餐");
assert.equal(formatCompensationSummary(items[2]), "-");

assert.equal(resolveAftersaleStatusTone("OPEN"), "orange");
assert.equal(resolveAftersaleStatusTone("RESOLVED"), "green");
assert.equal(resolveAftersaleStatusTone("OTHER"), "gray");

console.log("aftersale helpers test: ok");
