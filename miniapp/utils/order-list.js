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

function resolveVisibleOrders(items, targetOrderIds) {
  if (!targetOrderIds || !targetOrderIds.length) {
    return items;
  }
  // 支持多个目标订单：一次下单（午餐+晚餐）的关联订单应全部展示，
  // 不能只筛第一个导致晚餐订单在列表里"消失"。
  const idSet = new Set(targetOrderIds.map(String));
  return (items || []).filter((item) => idSet.has(String(item.id)));
}

module.exports = {
  mapOrderForDisplay,
  resolveOrderSourceText,
  resolveVisibleOrders
};
