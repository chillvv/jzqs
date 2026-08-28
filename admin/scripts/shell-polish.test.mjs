import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const layout = fs.readFileSync(path.join(root, "src/app/layout/AdminLayout.tsx"), "utf8");
const dashboard = fs.readFileSync(path.join(root, "src/modules/dashboard/DashboardPage.tsx"), "utf8");
const settings = fs.readFileSync(path.join(root, "src/modules/settings/SystemSettingsPage.tsx"), "utf8");

for (const banned of ["PRIVATE DINING ADMIN", "运营后台", "通知中心", "帮助与说明"]) {
  assert.equal(layout.includes(banned), false, `AdminLayout should not contain ${banned}`);
}

for (const required of ['"/analysis"', '"/orders"', '"/customers"']) {
  assert.equal(dashboard.includes(required), true, `DashboardPage should contain ${required}`);
}
assert.equal(dashboard.includes('onClick={item.action}'), true, "DashboardPage detail cards should navigate on click");

assert.equal(dashboard.includes("决定今天履约是否稳定"), false, "DashboardPage should not contain guide copy");
assert.equal(dashboard.includes("等待厨房确认接单"), false, "DashboardPage should not contain guide copy");
assert.equal(settings.includes('display: "none"'), false, "SystemSettingsPage should not keep hidden duplicate blocks");
assert.equal(settings.includes("当前风险提醒"), false, "SystemSettingsPage should not keep duplicate reminder copy");

console.log("shell polish test: ok");
