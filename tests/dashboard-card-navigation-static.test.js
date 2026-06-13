const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

const repoRoot = path.resolve(__dirname, "..");
const dashboard = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dashboard", "DashboardPage.tsx"),
  "utf8"
);
const css = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "index.css"),
  "utf8"
);

assert.match(
  dashboard,
  /navigate\("\/orders"|navigate\('\/orders'/,
  "看板至少应支持跳转到订单列表"
);
assert.match(
  dashboard,
  /navigate\("\/aftersales"|navigate\('\/aftersales'/,
  "看板至少应支持跳转到售后列表"
);
assert.match(
  dashboard,
  /dashboard-bi__metric-card--clickable/,
  "看板数据卡片应声明可点击样式类"
);
assert.match(
  css,
  /\.dashboard-bi__metric-card--clickable[\s\S]*cursor:\s*pointer/,
  "可点击卡片应有手型光标反馈"
);

console.log("PASS: 看板快捷跳转静态约束已锁定");
