const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "..");
const analysisPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "analysis", "OperationsAnalysisPage.tsx"),
  "utf8"
);
const dispatchHomePage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dispatch", "DispatchHomePage.tsx"),
  "utf8"
);
const dispatchProgressPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dispatch", "DispatchProgressPage.tsx"),
  "utf8"
);
const adminStyles = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "index.css"),
  "utf8"
);

assert.equal(
  analysisPage.includes("submittingAddCost"),
  true,
  "经营分析页的确认录入按钮应有 submitting 状态，避免重复录入成本"
);

assert.equal(
  analysisPage.includes("提交中..."),
  true,
  "经营分析页的确认录入按钮应明确展示提交中提示"
);

assert.equal(
  dispatchHomePage.includes("submittingDelete"),
  true,
  "调度首页删除确认按钮应有 submitting 状态，避免重复删除"
);

assert.equal(
  dispatchHomePage.includes("确认删除中..."),
  true,
  "调度首页删除确认按钮应明确展示删除中提示"
);

assert.equal(
  dispatchHomePage.includes("先勾选此单后再确认归属"),
  false,
  "分单工作台不应再保留单条确认归属提示"
);

assert.equal(
  dispatchHomePage.includes("确认归属"),
  false,
  "分单工作台不应再保留单条确认归属按钮"
);

assert.equal(
  dispatchHomePage.includes("handleSingleAssign"),
  false,
  "分单工作台不应再保留单条归区处理逻辑"
);

assert.equal(
  dispatchHomePage.includes("inlineAreas"),
  false,
  "分单工作台不应再保留每行单独区域选择状态"
);

assert.equal(
  dispatchHomePage.includes("批量归入区域"),
  true,
  "分单工作台应继续保留批量归区入口"
);

assert.equal(
  dispatchHomePage.includes("归区"),
  true,
  "分单工作台批量操作按钮应使用更短的归区文案"
);

assert.equal(
  adminStyles.includes(".dispatch-pending-shell--sticky"),
  true,
  "后台样式应定义分单工作台框内滚动布局"
);

assert.equal(
  adminStyles.includes(".dispatch-bulk-bar--sticky"),
  true,
  "后台样式应定义分单工作台吸顶批量工具栏"
);

assert.equal(
  dispatchProgressPage.includes("dispatch-progress-list"),
  true,
  "骑手进度页应使用竖向列表容器类名"
);

assert.equal(
  adminStyles.includes(".dispatch-progress-list"),
  true,
  "后台样式应定义骑手进度竖向列表样式"
);

assert.equal(
  dispatchProgressPage.includes("dispatch-progress-order-list"),
  true,
  "骑手进度弹窗里的订单队列也应使用单列列表容器"
);

assert.equal(
  adminStyles.includes(".dispatch-progress-order-list"),
  true,
  "后台样式应定义骑手进度弹窗订单单列列表样式"
);

assert.equal(
  dispatchProgressPage.includes("findFirstPendingOrderId"),
  true,
  "骑手进度页应基于区域顺序推断当前配送订单"
);

assert.equal(
  dispatchProgressPage.includes("order.orderId === item.currentOrderId"),
  false,
  "骑手进度页不应继续直接用进度接口的 currentOrderId 标记当前配送"
);

console.log("PASS: 分析页和调度首页关键按钮提交流程静态验收通过");
