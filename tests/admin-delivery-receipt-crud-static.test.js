const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

const repoRoot = path.resolve(__dirname, "..");
const deliveryService = fs.readFileSync(
  path.join(repoRoot, "backend", "src", "main", "java", "com", "jzqs", "app", "delivery", "service", "impl", "DeliveryServiceImpl.java"),
  "utf8"
);

assert.match(deliveryService, /UPDATE delivery_receipts[\s\S]*WHERE meal_slot_order_id = \?/, "已有回执时应走更新逻辑而不是继续重复插入");
assert.match(deliveryService, /walletAction", "UNCHANGED"/, "修改已有回执时不应再次重复扣餐");
assert.match(deliveryService, /INSERT INTO delivery_receipts/, "首次提交回执仍应保留创建逻辑");

console.log("PASS: 后台回执增改链路静态验收通过");
