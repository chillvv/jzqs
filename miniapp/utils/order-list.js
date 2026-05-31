const { formatMonthDay, periodLabel, statusClass } = require('./mobile');
const { resolveOrderActions, resolveOrderStatusText } = require('./aftersale');
const { getReceiptDisplayState } = require('./receipt-display');

function resolveOrderSourceText(source) {
  if (source === 'BACKEND') {
    return '后台代下单';
  }
  if (source === 'SUBSCRIPTION') {
    return '固定订餐';
  }
  return '自主下单';
}

function buildOrderMetaText(item, sourceText) {
  if (item.deliveredAt) {
    return `${sourceText} · 送达时间 ${item.deliveredAt}`;
  }
  return sourceText;
}

function mapOrderForDisplay(item) {
  const receiptState = getReceiptDisplayState(item);
  const actionState = resolveOrderActions({
    status: item.status,
    serveDate: item.serveDate,
    now: new Date().toISOString(),
    afterSaleOpen: item.afterSaleOpen
  });

  let displayStatusClass = statusClass(item.status === 'REFUNDED' ? 'CANCELLED' : item.status);
  if (item.afterSaleStatus === 'REJECTED') {
    displayStatusClass = 'cancelled';
  }

  return {
    ...item,
    serveDateText: formatMonthDay(item.serveDate),
    periodText: periodLabel(item.mealPeriod),
    statusText: resolveOrderStatusText(item),
    statusClass: displayStatusClass,
    sourceText: resolveOrderSourceText(item.source),
    showReceiptImage: receiptState.canShowReceiptImage,
    receiptHint: receiptState.receiptHint,
    canViewReceipt: item.status === 'DELIVERED',
    canCancel: actionState.canCancel,
    canApplyAftersale: actionState.canApplyAftersale,
    actionText: actionState.actionText,
    orderPrimaryActionText: '订单详情',
    orderMetaText: buildOrderMetaText(item, resolveOrderSourceText(item.source))
  };
}

module.exports = {
  mapOrderForDisplay,
  resolveOrderSourceText
};
