const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

const repoRoot = path.resolve(__dirname, "..");
const layout = fs.readFileSync(path.join(repoRoot, "admin", "src", "app", "layout", "AdminLayout.tsx"), "utf8");
const realtime = fs.readFileSync(path.join(repoRoot, "admin", "src", "shared", "realtime", "adminRealtime.ts"), "utf8");

assert.doesNotMatch(
  layout,
  /startAdminRealtime\(\)/,
  "AdminLayout 不应在所有后台页面全局启动 WebSocket"
);

assert.match(
  realtime,
  /if \(listeners\.size === 0\) \{\s*stopAdminRealtime\(\);/s,
  "最后一个监听器移除时应主动关闭 WebSocket，避免离开配送页后仍持续重连"
);

console.log("PASS: admin realtime 已收敛到按需连接");
