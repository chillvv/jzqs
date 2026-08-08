const { formatMonthDay, periodLabel, statusClass, normalizeCustomerStatus } = require('./mobile');
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
  return sourceText;
}

function resolveDisplayStatusClass(item, visibleStatus) {
  // 售后状态优先覆盖履约状态，让用户一眼看出订单卡在哪一步
  if (item.afterSaleStatus === 'REJECTED') {
    return 'rejected';
  }
  if (item.afterSaleStatus === 'PENDING' || item.afterSaleStatus === 'PROCESSING') {
    return 'aftersale';
  }
  return statusClass(visibleStatus);
}

function mapOrderForDisplay(item) {
  const receiptState = getReceiptDisplayState(item);
  const visibleStatus = normalizeCustomerStatus(item.userVisibleStatus || item.status);
  const actionState = resolveOrderActions({
    status: item.status,
    userVisibleStatus: visibleStatus,
    serveDate: item.serveDate,
    now: new Date().toISOString(),
    afterSaleOpen: item.afterSaleOpen
  });

  return {
    ...item,
    serveDateText: formatMonthDay(item.serveDate),
    periodText: periodLabel(item.mealPeriod),
    statusText: resolveOrderStatusText(item),
    statusClass: resolveDisplayStatusClass(item, visibleStatus),
    customerStatus: visibleStatus,
    sourceText: resolveOrderSourceText(item.source),
    showReceiptImage: receiptState.canShowReceiptImage,
    receiptHint: receiptState.receiptHint,
    canViewReceipt: visibleStatus === 'DELIVERED',
    canCancel: actionState.canCancel,
    canApplyAftersale: actionState.canApplyAftersale,
    isAftersaleProcessing: actionState.isAftersaleProcessing,
    supportRefundStage: actionState.supportRefundStage || '',
    actionText: actionState.actionText,
    orderPrimaryActionText: '订单详情',
    orderMetaText: buildOrderMetaText(item, resolveOrderSourceText(item.source))
  };
}

function resolveVisibleOrders(items, targetOrderId) {
  if (!targetOrderId) {
    return items;
  }
  const matchedItem = (items || []).find((item) => String(item.id) === String(targetOrderId));
  return matchedItem ? [matchedItem] : [];
}

module.exports = {
  mapOrderForDisplay,
  resolveOrderSourceText,
  resolveVisibleOrders
};
