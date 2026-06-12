const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "..");
const analysisPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "analysis", "OperationsAnalysisPage.tsx"),
  "utf8"
);
const dispatchHomePage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dispatch", "DispatchHomePage.tsx"),
  "utf8"
);

assert.equal(
  analysisPage.includes("submittingAddCost"),
  true,
  "经营分析页的确认录入按钮应有 submitting 状态，避免重复录入成本"
);

assert.equal(
  analysisPage.includes("提交中..."),
  true,
  "经营分析页的确认录入按钮应明确展示提交中提示"
);

assert.equal(
  dispatchHomePage.includes("submittingDelete"),
  true,
  "调度首页删除确认按钮应有 submitting 状态，避免重复删除"
);

assert.equal(
  dispatchHomePage.includes("确认删除中..."),
  true,
  "调度首页删除确认按钮应明确展示删除中提示"
);

console.log("PASS: 分析页和调度首页关键按钮提交流程静态验收通过");
