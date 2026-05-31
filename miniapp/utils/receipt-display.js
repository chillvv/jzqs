const { formatMonthDay, periodLabel } = require('./mobile');

function resolveOrderSourceText(source) {
  if (source === 'BACKEND') {
    return '后台代下单';
  }
  if (source === 'SUBSCRIPTION') {
    return '固定订餐';
  }
  return '自主下单';
}

function getReceiptDisplayState(item) {
  const canShowReceiptImage = Boolean(item.receiptUrl) && Boolean(item.receiptVisible);
  const receiptHint = item.status === 'DELIVERED' && item.receiptUrl && !canShowReceiptImage
    ? (item.mealPeriod === 'LUNCH' ? '配送已完成，图片将于 11:30 后可见' : '配送已完成，图片将于 17:00 后可见')
    : '';
  return {
    canShowReceiptImage,
    receiptHint
  };
}

function mapReceiptRecord(item) {
  return {
    ...item,
    serveDateText: formatMonthDay(item.serveDate),
    periodText: periodLabel(item.mealPeriod),
    sourceText: resolveOrderSourceText(item.source),
    ...getReceiptDisplayState(item)
  };
}

module.exports = {
  getReceiptDisplayState,
  mapReceiptRecord
};
