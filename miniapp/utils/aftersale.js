const { canCancelMiniappOrder } = require("./order-guards");
const { request } = require("./request");
const { statusLabel, transactionLabel } = require("./mobile");

function resolveOrderActions({ status, userVisibleStatus, serveDate, now, afterSaleOpen }) {
  const visibleStatus = userVisibleStatus || status;
  if (afterSaleOpen) {
    return { canCancel: false, canApplyAftersale: false, isAftersaleProcessing: true, actionText: "处理中" };
  }
  if (canCancelMiniappOrder({ status: visibleStatus, serveDate, now })) {
    return { canCancel: true, canApplyAftersale: false, actionText: "取消订单" };
  }
  if (visibleStatus === "PENDING_DISPATCH" || visibleStatus === "DELIVERED") {
    return { canCancel: false, canApplyAftersale: true, actionText: "申请售后" };
  }
  return { canCancel: false, canApplyAftersale: false, actionText: "" };
}

function resolveOrderStatusText({ status, userVisibleStatus, afterSaleStatus }) {
  const visibleStatus = userVisibleStatus || status;
  if (status === "REFUNDED") {
    return "已退款";
  }
  if (afterSaleStatus === "PENDING" || afterSaleStatus === "PROCESSING") {
    return "售后处理中";
  }
  if (afterSaleStatus === "REJECTED") {
    return "售后未通过";
  }
  return statusLabel(visibleStatus);
}

function buildRejectedAftersaleDetail(adminRemark) {
  const detail = adminRemark && adminRemark.trim()
    ? adminRemark.trim()
    : "后台暂未填写说明，请联系客服处理。";
  return `售后申请未通过。\n处理结果：${detail}`;
}

function submitAftersaleApplication(orderId, payload) {
  return request({
    url: `/api/mobile/customer/orders/${orderId}/after-sales`,
    method: "POST",
    data: payload
  });
}

function resolveWalletTransactionTitle(item) {
  if (item.transactionType === "OPEN") {
    return "开卡";
  }
  if (item.transactionType === "REFUND_RETURN") {
    return "退款退回";
  }
  return transactionLabel(item.transactionType);
}

function resolveWalletTransactionStatusText(item) {
  if (item.transactionType === "REFUND_RETURN") {
    return "退款已回补";
  }
  if (item.refunded) {
    return "已退款";
  }
  return "";
}

function resolveWalletTransactionRemark(item) {
  if (item.transactionType === "OPEN") {
    return item.remark || "餐包开通";
  }
  if (item.transactionType === "REFUND_RETURN" && item.refundReasonText) {
    return `退款原因：${item.refundReasonText}`;
  }
  if (item.refunded && item.refundReasonText) {
    return `原扣餐已退款，原因：${item.refundReasonText}`;
  }
  if (item.refunded) {
    return "原扣餐已退款";
  }
  return item.remark || "";
}

function formatChineseDate(value) {
  const raw = String(value || "").trim();
  if (!raw) {
    return "";
  }
  const normalized = raw.replace("T", " ");
  const datePart = normalized.slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(datePart)) {
    return raw;
  }
  const [year, month, day] = datePart.split("-");
  return `${year}年${Number(month)}月${Number(day)}日`;
}

function formatWalletTransaction(item) {
  return {
    ...item,
    title: resolveWalletTransactionTitle(item),
    deltaText: `${item.mealDelta > 0 ? "+" : ""}${item.mealDelta}`,
    statusText: resolveWalletTransactionStatusText(item),
    remarkText: resolveWalletTransactionRemark(item),
    displayCreatedAt: formatChineseDate(item.createdAt)
  };
}

module.exports = {
  resolveOrderActions,
  resolveOrderStatusText,
  buildRejectedAftersaleDetail,
  submitAftersaleApplication,
  formatWalletTransaction
};
