const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

const repoRoot = path.resolve(__dirname, "..");
const orderPrepHelpers = fs.readFileSync(path.join(repoRoot, "admin", "src", "modules", "orders", "orderPrepPage.helpers.ts"), "utf8");
const orderPrepPage = fs.readFileSync(path.join(repoRoot, "admin", "src", "modules", "orders", "OrderPrepPage.tsx"), "utf8");
const manualCreateHelpers = fs.readFileSync(path.join(repoRoot, "admin", "src", "modules", "orders", "manualCreateOrder.helpers.ts"), "utf8");
const adminTypes = fs.readFileSync(path.join(repoRoot, "admin", "src", "shared", "api", "types.ts"), "utf8");
const adminHttp = fs.readFileSync(path.join(repoRoot, "admin", "src", "shared", "api", "http.ts"), "utf8");
const orderPrepService = fs.readFileSync(path.join(repoRoot, "backend", "src", "main", "java", "com", "jzqs", "app", "order", "service", "impl", "OrderPrepServiceImpl.java"), "utf8");
const manualCreateRequest = fs.readFileSync(path.join(repoRoot, "backend", "src", "main", "java", "com", "jzqs", "app", "order", "api", "ManualCreateOrderRequest.java"), "utf8");
const subscriptionRuleRequest = fs.readFileSync(path.join(repoRoot, "backend", "src", "main", "java", "com", "jzqs", "app", "subscription", "api", "SubscriptionRuleRequest.java"), "utf8");
const subscriptionRuleResponse = fs.readFileSync(path.join(repoRoot, "backend", "src", "main", "java", "com", "jzqs", "app", "subscription", "api", "SubscriptionRuleResponse.java"), "utf8");
const riderQueueResponse = fs.readFileSync(path.join(repoRoot, "backend", "src", "main", "java", "com", "jzqs", "app", "mobile", "api", "RiderQueueItemResponse.java"), "utf8");

assert.match(orderPrepHelpers, /function isMeaningfulRemark/, "订单备注判定应收敛到统一 helper");
assert.match(orderPrepHelpers, /value !== "-"|trimmed !== "-"/, "订单备注判定应忽略后端占位符 '-'");
assert.doesNotMatch(orderPrepService, /COALESCE\(mso\.user_note, mso\.note, '-'\)/, "后端订单查询不应再把空备注兜成 '-'");
assert.match(orderPrepPage, /出餐 \/ 配送|deliveryMealPeriod/, "订单页应展示出餐餐次和配送餐次");

assert.match(manualCreateHelpers, /deliveryMealPeriod/, "录入代客订单 payload 应支持单独的配送餐次");
assert.match(adminTypes, /deliveryMealPeriod/, "前端订单类型应暴露配送餐次字段");
assert.match(adminHttp, /deliveryMealPeriod/, "前端下单接口应上传配送餐次");
assert.match(manualCreateRequest, /deliveryMealPeriod/, "后台录单请求应支持配送餐次");
assert.match(subscriptionRuleRequest, /lunchDeliveryMealPeriod|dinnerDeliveryMealPeriod/, "固定订餐请求应支持每个餐次的配送餐次");
assert.match(subscriptionRuleResponse, /lunchDeliveryMealPeriod|dinnerDeliveryMealPeriod/, "固定订餐响应应返回每个餐次的配送餐次");
assert.match(orderPrepService, /delivery_meal_period|deliveryMealPeriod/, "订单服务实现应落库并读取配送餐次");
assert.match(riderQueueResponse, /productionMealPeriod|deliveryMealPeriod/, "骑手队列响应应区分出餐餐次和配送餐次");

console.log("PASS: 订单备注误判修复与配送餐次链路已落地");
