const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "..");
const customerPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "customers", "CustomerAssetPage.tsx"),
  "utf8"
);
const menuPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "menu", "MenuSchedulePage.tsx"),
  "utf8"
);
const subscriptionPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "orders", "SubscriptionManagementTab.tsx"),
  "utf8"
);
const lowBalanceModal = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dashboard", "LowBalanceAlertModal.tsx"),
  "utf8"
);
const dispatchAreasPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dispatch", "DispatchAreasPage.tsx"),
  "utf8"
);

assert.equal(
  customerPage.includes("submittingProfile"),
  true,
  "客户中心保存资料按钮应有 submitting 状态"
);

assert.equal(
  customerPage.includes("submittingAddress"),
  true,
  "客户中心地址编辑和删除操作应有 submitting 状态"
);

assert.equal(
  customerPage.includes("submittingGrant"),
  true,
  "客户中心确认充值按钮应有 submitting 状态"
);

assert.equal(
  customerPage.includes("submittingCreate"),
  true,
  "客户中心确认创建按钮应有 submitting 状态"
);

assert.equal(
  customerPage.includes("submittingDeduct"),
  true,
  "客户中心确认扣减按钮应有 submitting 状态"
);

assert.equal(
  menuPage.includes("savingDay"),
  true,
  "周菜单页保存当天按钮应有 submitting 状态"
);

assert.equal(
  menuPage.includes("publishing"),
  true,
  "周菜单页确认发布按钮应有 publishing 状态"
);

assert.equal(
  menuPage.includes("copyingLastWeek"),
  true,
  "周菜单页复制上周菜单按钮应有 submitting 状态"
);

assert.equal(
  menuPage.includes("creatingNextWeek"),
  true,
  "周菜单页新建下周模板按钮应有 submitting 状态"
);

assert.equal(
  subscriptionPage.includes("togglingRuleId"),
  true,
  "固定订餐页暂停/恢复按钮应有 submitting 状态"
);

assert.equal(
  subscriptionPage.includes("deletingRuleId"),
  true,
  "固定订餐页删除按钮应有 submitting 状态"
);

assert.equal(
  lowBalanceModal.includes("submittingDormantCustomerId"),
  true,
  "低余额预警弹窗标记沉睡按钮应有 submitting 状态"
);

assert.equal(
  lowBalanceModal.includes("标记中..."),
  true,
  "低余额预警弹窗应明确展示标记中提示"
);

assert.equal(
  dispatchAreasPage.includes("submittingDeleteOrder"),
  true,
  "分区调度页删除订单确认按钮应有 submitting 状态"
);

assert.equal(
  dispatchAreasPage.includes("确认删除中..."),
  true,
  "分区调度页删除订单确认按钮应明确展示删除中提示"
);

console.log("PASS: 后台更多关键按钮提交流程静态验收通过");
