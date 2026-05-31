const assert = require("node:assert/strict");
const {
  resolveOrderActions,
  resolveOrderStatusText,
  formatWalletTransaction,
  buildRejectedAftersaleDetail
} = require("../utils/aftersale");

assert.deepEqual(
  resolveOrderActions({
    status: "PENDING_DISPATCH",
    serveDate: "2026-05-27",
    now: "2026-05-26T23:30:00",
    afterSaleOpen: false
  }),
  { canCancel: false, canApplyAftersale: true, actionText: "申请售后" }
);

assert.equal(resolveOrderStatusText({ status: "REFUNDED", afterSaleStatus: "COMPLETED" }), "已退款");
assert.equal(resolveOrderStatusText({ status: "PENDING_DISPATCH", afterSaleStatus: "PENDING" }), "售后处理中");
assert.equal(resolveOrderStatusText({ status: "DELIVERED", afterSaleStatus: "REJECTED" }), "售后未通过");

assert.equal(
  buildRejectedAftersaleDetail("餐已正常送达，本次不满足退款条件"),
  "售后申请未通过。\n处理结果：餐已正常送达，本次不满足退款条件"
);

assert.equal(
  buildRejectedAftersaleDetail(""),
  "售后申请未通过。\n处理结果：后台暂未填写说明，请联系客服处理。"
);

assert.deepEqual(
  formatWalletTransaction({
    id: 1,
    transactionType: "RESERVE",
    mealDelta: -1,
    remark: "用户自主下单占用餐次",
    refunded: true,
    refundReasonText: "临时有事，不需要这餐"
  }),
  {
    id: 1,
    transactionType: "RESERVE",
    mealDelta: -1,
    remark: "用户自主下单占用餐次",
    refunded: true,
    refundReasonText: "临时有事，不需要这餐",
    title: "下单占用",
    deltaText: "-1",
    statusText: "已退款",
    remarkText: "原扣餐已退款，原因：临时有事，不需要这餐"
  }
);

assert.deepEqual(
  formatWalletTransaction({
    id: 2,
    transactionType: "REFUND_RETURN",
    mealDelta: 1,
    remark: "已退款退回餐次",
    refunded: false,
    refundReasonText: "临时有事，不需要这餐"
  }),
  {
    id: 2,
    transactionType: "REFUND_RETURN",
    mealDelta: 1,
    remark: "已退款退回餐次",
    refunded: false,
    refundReasonText: "临时有事，不需要这餐",
    title: "退款退回",
    deltaText: "+1",
    statusText: "退款已回补",
    remarkText: "退款原因：临时有事，不需要这餐"
  }
);

console.log("aftersale tests passed");
