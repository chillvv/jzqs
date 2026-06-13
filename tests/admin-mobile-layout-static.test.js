const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

const repoRoot = path.resolve(__dirname, "..");
const layout = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "app", "layout", "AdminLayout.tsx"),
  "utf8"
);
const css = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "index.css"),
  "utf8"
);

assert.match(layout, /mobile-sidebar-toggle/, "后台移动端应提供汉堡菜单按钮");
assert.match(layout, /mobileSidebarOpen/, "后台布局应存在移动端侧边栏展开状态");
assert.match(css, /@media\s*\(max-width:\s*480px\)/, "应存在 480px 以下后台适配规则");
assert.match(css, /min-height:\s*44px/, "移动端按钮最小高度应至少为 44px");
assert.match(
  css,
  /sidebar--mobile-open|transform:\s*translateX\(0\)/,
  "移动端侧边栏应支持展开态样式"
);
assert.match(
  css,
  /width:\s*100vw|height:\s*100vh|modal-content--fullscreen/,
  "移动端弹窗应具备全屏适配能力"
);

console.log("PASS: 后台移动端适配静态约束已锁定");
