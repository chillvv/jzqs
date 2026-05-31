import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

const checks = [
  {
    file: "index.html",
    includes: ["轻知膳食商家后台"]
  },
  {
    file: "src/app/layout/AdminLayout.tsx",
    includes: ["运行看板", "客户资产", "菜单排期", "订单备餐", "骑手派单", "系统设置", "轻知膳食 SaaS", "商家后台"]
  }
];

let hasError = false;

for (const check of checks) {
  const filePath = path.join(root, check.file);
  const content = fs.readFileSync(filePath, "utf8");

  for (const expected of check.includes) {
    if (!content.includes(expected)) {
      hasError = true;
      console.error(`Missing expected copy "${expected}" in ${check.file}`);
    }
  }
}

if (hasError) {
  process.exit(1);
}

console.log("Copy verification passed.");
