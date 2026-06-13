const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

const repoRoot = path.resolve(__dirname, "..");
const customerPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "customers", "CustomerAssetPage.tsx"),
  "utf8"
);
const customerService = fs.readFileSync(
  path.join(
    repoRoot,
    "backend",
    "src",
    "main",
    "java",
    "com",
    "jzqs",
    "app",
    "customer",
    "service",
    "impl",
    "CustomerAssetServiceImpl.java"
  ),
  "utf8"
);

assert.match(customerPage, /deductDisabled/, "客户扣餐界面应计算扣餐禁用状态");
assert.match(customerPage, /余额不足/, "客户扣餐界面应明确提示余额不足");
assert.match(
  customerPage,
  /disabled=\{[^}]*deductDisabled/,
  "余额不足时扣餐按钮应置灰"
);
assert.doesNotMatch(customerPage, /window\.alert/, "客户页交互失败提示应统一改为 toast 而不是原生 alert");
assert.doesNotMatch(customerPage, /window\.confirm/, "客户页删除应改为二次确认弹窗而不是原生 confirm");
assert.match(customerPage, /确定要删除吗|确认删除/, "客户页删除动作应有明确确认弹窗文案");
assert.match(
  customerService,
  /WALLET_BALANCE_NOT_ENOUGH/,
  "后端扣餐接口应继续保留余额不足异常"
);

console.log("PASS: 客户扣餐防透支静态约束已锁定");
