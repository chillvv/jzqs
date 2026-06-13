const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

const repoRoot = path.resolve(__dirname, "..");
const promptSensitiveFiles = [
  "admin/src/modules/dispatch/DispatchHomePage.tsx",
  "admin/src/modules/dispatch/DispatchAreasPage.tsx",
  "admin/src/modules/orders/SubscriptionManagementTab.tsx",
  "admin/src/modules/orders/SubscriptionRuleForm.tsx",
  "admin/src/modules/menu/MenuSchedulePage.tsx",
  "admin/src/modules/dashboard/LowBalanceAlertModal.tsx",
  "admin/src/modules/analysis/OperationsAnalysisPage.tsx",
  "admin/src/modules/settings/SystemSettingsPage.tsx",
  "admin/src/modules/dispatch/DispatchProgressPage.tsx",
  "admin/src/modules/maintenance/MaintenancePage.tsx"
];

for (const relativePath of promptSensitiveFiles) {
  const source = fs.readFileSync(path.join(repoRoot, relativePath), "utf8");
  assert.doesNotMatch(source, /window\.alert/, `${relativePath} 不应继续使用原生 alert`);
  assert.doesNotMatch(source, /window\.confirm/, `${relativePath} 不应继续使用原生 confirm`);
}

console.log("PASS: admin 活跃页面原生提示已统一清理");
