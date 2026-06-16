const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "..");
const orderPrepPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "orders", "OrderPrepPage.tsx"),
  "utf8"
);

assert.equal(
  orderPrepPage.includes("submittingManualCreate"),
  true,
  "代客录单确认按钮应有 submitting 状态，避免重复录入"
);

assert.equal(
  orderPrepPage.includes("submittingAssign"),
  true,
  "分配骑手确认按钮应有 submitting 状态，避免重复分配"
);

assert.equal(
  orderPrepPage.includes("submittingReceipt"),
  true,
  "提交回执按钮应有 submitting 状态，避免重复提交回执"
);

assert.equal(
  orderPrepPage.includes("submittingEdit"),
  true,
  "保存订单按钮应有 submitting 状态，避免重复保存"
);

assert.equal(
  orderPrepPage.includes("submittingDelete"),
  true,
  "确认删除按钮应有 submitting 状态，避免重复删除"
);

assert.equal(
  orderPrepPage.includes("submittingConsumeDelivered"),
  false,
  "订单页应移除批量核销扣餐逻辑"
);

assert.equal(
  orderPrepPage.includes("processingConfirmationId"),
  true,
  "固定订餐确认生成按钮应有按单 submitting 状态，避免重复生成"
);

assert.equal(
  orderPrepPage.includes("生成中..."),
  true,
  "订单页应明确展示关键按钮的处理中提示"
);

console.log("PASS: 后台订单页关键按钮提交流程静态验收通过");
