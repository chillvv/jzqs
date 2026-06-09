const { formatMonthDay, periodLabel } = require('./mobile');
const { resolveMediaUrl } = require('./media-url');

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
  return {
    canShowReceiptImage,
    receiptHint: ''
  };
}

function mapReceiptRecord(item, baseUrl) {
  return {
    ...item,
    receiptUrl: resolveMediaUrl(item.receiptUrl, baseUrl),
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
