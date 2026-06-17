const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "..");
const source = fs.readFileSync(
  path.join(repoRoot, "backend", "src", "main", "java", "com", "jzqs", "app", "mobile", "MobilePortalServiceImpl.java"),
  "utf8"
);

assert.match(
  source,
  /attemptAutoAssignPendingOrders\(/,
  "小程序下单流程应通过安全包装方法触发自动归区，避免副作用异常直接打断主下单"
);

assert.doesNotMatch(
  source,
  /dispatchService\.autoAssignPendingOrders\(normalizedMealPeriod\);/,
  "小程序下单流程不应直接裸调自动归区"
);

assert.match(
  source,
  /attemptPublishCustomerEvent\(/,
  "小程序下单流程应通过安全包装方法发布实时事件，避免通知异常导致下单 500"
);

console.log("PASS: 小程序下单链路韧性静态约束通过");
