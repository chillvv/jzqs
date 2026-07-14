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
  let canShowReceiptImage = Boolean(item.receiptUrl) && Boolean(item.receiptVisible);
  let receiptHint = '';

  if (!item.receiptUrl && !item.receiptNote && item.userVisibleStatus === 'DELIVERED') {
    return {
      canShowReceiptImage: false,
      receiptHint: '回执内容已清理，仅保留送达状态'
    };
  }

  if (canShowReceiptImage && item.serveDate) {
    const today = new Date();
    // Use local timezone formatting to get YYYY-MM-DD
    const tzOffset = today.getTimezoneOffset() * 60000;
    const localISOTime = (new Date(today.getTime() - tzOffset)).toISOString().split('T')[0];
    
    if (item.serveDate < localISOTime) {
      canShowReceiptImage = false;
      receiptHint = '回执照片仅送餐当天可查看';
    }
  }

  return {
    canShowReceiptImage,
    receiptHint
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
