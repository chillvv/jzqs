import assert from "node:assert/strict";
import {
  buildCustomerPortfolioSummary,
  buildCustomerAssetStats,
  buildCustomerOverviewSummary,
  filterCustomerAssets,
  resolveCustomerOrderModeLabel,
  resolveCustomerStatusLabel,
  resolveCustomerSpecialLabel
} from "../temp-test/modules/customers/customerAssetPage.helpers.js";

const items = [
  {
    id: 1,
    name: "张先生",
    phone: "13800000001",
    customerStatus: "INTENTION",
    packageName: "",
    totalMeals: 0,
    remainingMeals: 0,
    hasOpenedCard: false,
    fixedSubscriptionEnabled: false,
    priorityCustomer: false,
    priorityTag: null,
    remark: "准备开卡",
    lastOrderAt: null,
    registeredAt: "2026-05-01 08:00:00",
    status: "ACTIVE"
  },
  {
    id: 2,
    name: "李女士",
    phone: "13800000002",
    customerStatus: "FORMAL",
    packageName: "33餐月卡套餐",
    totalMeals: 33,
    remainingMeals: 12,
    hasOpenedCard: true,
    fixedSubscriptionEnabled: true,
    priorityCustomer: true,
    priorityTag: "新开卡优先",
    remark: "重点关注",
    lastOrderAt: "2026-05-13 11:30:00",
    registeredAt: "2026-05-01 08:00:00",
    status: "ACTIVE"
  },
  {
    id: 3,
    name: "王女士",
    phone: "13800000003",
    customerStatus: "DORMANT",
    packageName: "周卡",
    totalMeals: 7,
    remainingMeals: 0,
    hasOpenedCard: true,
    fixedSubscriptionEnabled: false,
    priorityCustomer: false,
    priorityTag: null,
    remark: "很久未下单",
    lastOrderAt: "2025-12-01 18:00:00",
    registeredAt: "2025-10-01 08:00:00",
    status: "EXHAUSTED"
  },
  {
    id: 4,
    name: "陈先生",
    phone: "13800000004",
    customerStatus: "FORMAL",
    packageName: "22餐轻食卡",
    totalMeals: 22,
    remainingMeals: 2,
    hasOpenedCard: true,
    fixedSubscriptionEnabled: false,
    priorityCustomer: false,
    priorityTag: null,
    remark: "普通客户",
    lastOrderAt: "2026-05-12 12:00:00",
    registeredAt: "2026-04-01 08:00:00",
    status: "ACTIVE"
  }
];

{
  const stats = buildCustomerAssetStats(items);
  assert.deepEqual(stats, {
    intentionCount: 1,
    formalCount: 2,
    dormantCount: 1,
    priorityCount: 1,
    fixedSubscriptionCount: 1,
    balanceCount: 2,
    noBalanceCount: 2
  });
}

{
  const filtered = filterCustomerAssets(items, {
    keyword: "13800000002",
    customerStatus: "FORMAL",
    balanceState: "HAS_BALANCE",
    priorityOnly: true,
    orderMode: "SUBSCRIPTION"
  });
  assert.deepEqual(filtered.map((item) => item.id), [2]);
}

{
  const filtered = filterCustomerAssets(items, {
    keyword: "",
    customerStatus: "ALL",
    balanceState: "LOW_BALANCE",
    priorityOnly: false,
    orderMode: "ALL"
  });
  assert.deepEqual(filtered.map((item) => item.id), [4]);
}

{
  const filtered = filterCustomerAssets(items, {
    keyword: "未下单",
    customerStatus: "ALL",
    balanceState: "NO_BALANCE",
    priorityOnly: false,
    orderMode: "NORMAL"
  });
  assert.deepEqual(filtered.map((item) => item.id), [3]);
}

{
  const summary = buildCustomerPortfolioSummary(items);
  assert.deepEqual(summary, {
    lowBalanceCount: 1,
    exhaustedCount: 1,
    vipCount: 1,
    recentActiveCount: 2
  });
}

{
  const summary = buildCustomerPortfolioSummary(items);
  assert.deepEqual(buildCustomerOverviewSummary({
    formalCount: 2,
    noBalanceCount: 2,
    priorityCount: 1,
    fixedSubscriptionCount: 1
  }, summary), [
    {
      label: "正式客户",
      value: "2 人",
      tone: "blue"
    },
    {
      label: "无余额客户",
      value: "2 人",
      tone: "red"
    },
    {
      label: "固定订餐",
      value: "1 人",
      tone: "orange"
    }
  ]);
}

assert.equal(resolveCustomerOrderModeLabel(items[0]), "普通下单");
assert.equal(resolveCustomerOrderModeLabel(items[1]), "固定订餐");
assert.equal(resolveCustomerStatusLabel("INTENTION"), "意向客户");
assert.equal(resolveCustomerStatusLabel("FORMAL"), "正式客户");
assert.equal(resolveCustomerStatusLabel("DORMANT"), "沉睡客户");
assert.equal(resolveCustomerSpecialLabel(items[0]), "-");
assert.equal(resolveCustomerSpecialLabel(items[1]), "新开卡优先");

console.log("customer-asset helpers test: ok");
