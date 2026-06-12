const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "..");
const orderPrepPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "orders", "OrderPrepPage.tsx"),
  "utf8"
);
const subscriptionRuleForm = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "orders", "SubscriptionRuleForm.tsx"),
  "utf8"
);
const aftersalePage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "aftersales", "AftersalePage.tsx"),
  "utf8"
);
const dispatchRidersPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dispatch", "DispatchRidersPage.tsx"),
  "utf8"
);
const dispatchHomePage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dispatch", "DispatchHomePage.tsx"),
  "utf8"
);
const dispatchAreasPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dispatch", "DispatchAreasPage.tsx"),
  "utf8"
);

assert.equal(
  orderPrepPage.includes("submittingManualCreate ? undefined : closeManualCreateModal"),
  true,
  "订单页代客录单弹窗提交中时应禁止点右上角关闭"
);

assert.equal(
  orderPrepPage.includes("submittingOrderAftersale ? undefined : closeOrderAftersaleModal"),
  true,
  "订单页售后处理弹窗提交中时应禁止点右上角关闭"
);

assert.equal(
  orderPrepPage.includes("isSubmittingImport ? undefined : () => setIsSubscriptionPreviewOpen(false)"),
  true,
  "订单页自动导入弹窗生成中时应禁止点右上角关闭"
);

assert.equal(
  orderPrepPage.includes("submittingAssign ? undefined : () => setIsAssignOpen(false)"),
  true,
  "订单页分配骑手弹窗提交中时应禁止点右上角关闭"
);

assert.equal(
  orderPrepPage.includes("submittingReceipt ? undefined : () => setIsReceiptOpen(false)"),
  true,
  "订单页回执弹窗提交中时应禁止点右上角关闭"
);

assert.equal(
  orderPrepPage.includes("submittingEdit ? undefined : () => setIsEditOpen(false)"),
  true,
  "订单页编辑弹窗提交中时应禁止点右上角关闭"
);

assert.equal(
  subscriptionRuleForm.includes("className=\"modal-overlay\" onClick={submitting ? undefined : onClose}"),
  true,
  "固定订餐弹窗保存中时应禁止点击遮罩关闭"
);

assert.equal(
  subscriptionRuleForm.includes("className=\"modal-close\" onClick={submitting ? undefined : onClose}"),
  true,
  "固定订餐弹窗保存中时应禁止点右上角关闭"
);

assert.equal(
  aftersalePage.includes("className=\"modal-close\" onClick={submitting ? undefined : closeResolveModal}"),
  true,
  "售后处理弹窗提交中时应禁止点右上角关闭"
);

assert.equal(
  aftersalePage.includes("className=\"btn btn-outline\" onClick={closeResolveModal} disabled={submitting}"),
  true,
  "售后处理弹窗提交中时应禁止点取消"
);

assert.equal(
  dispatchRidersPage.includes("onClose={saving ? () => undefined : () => setShowAddModal(false)}"),
  true,
  "骑手编辑弹窗保存中时应禁止关闭"
);

assert.equal(
  dispatchRidersPage.includes("onClose={deleting ? () => undefined : () => setDeleteConfirmRider(null)}"),
  true,
  "骑手删除确认弹窗删除中时应禁止关闭"
);

assert.equal(
  dispatchHomePage.includes("onClose={submittingDelete ? () => undefined : () => setDeleteConfirmState(null)}"),
  true,
  "调度首页删除确认提交中时应禁止关闭"
);

assert.equal(
  dispatchAreasPage.includes("onClose={submittingDeleteOrder ? () => undefined : () => setDeleteConfirmState(null)}"),
  true,
  "分区调度删除确认提交中时应禁止关闭"
);

console.log("PASS: 后台弹窗关闭守卫静态验收通过");
