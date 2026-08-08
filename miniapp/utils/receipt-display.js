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

// 餐期释放时间：与后端 DeliverySubscriptionModule 的默认/读取规则保持一致
// LUNCH 11:30 / DINNER 17:00。送达回执需要等到对应餐期过了释放时间才对用户可见。
const RECEIPT_RELEASE_TIME = {
  LUNCH: '11:30',
  DINNER: '17:00'
};

function getLocalISODate(now) {
  const tzOffset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - tzOffset).toISOString().split('T')[0];
}

function resolveReleaseTimeText(mealPeriod) {
  if (!mealPeriod) return null;
  const key = String(mealPeriod).toUpperCase();
  return RECEIPT_RELEASE_TIME[key] || null;
}

function getReceiptDisplayState(item) {
  const hasReceiptUrl = Boolean(item.receiptUrl);
  const hasReceiptNote = Boolean(item.receiptNote);
  const receiptEverExisted = Boolean(item.receiptEverExisted);
  const today = getLocalISODate(new Date());
  const serveDate = item.serveDate;
  const isSameDay = serveDate === today;
  const isPastDay = serveDate && serveDate < today;

  // 1) 送达后骑手已上传回执，图片可见：直接展示。
  if (hasReceiptUrl && item.receiptVisible) {
    return { canShowReceiptImage: true, receiptHint: '' };
  }

  // 2) 送达日已过：图片统一不可再查看。
  //    - 原本有回执（被凌晨清理）→ 提示"仅当天可查看"。
  //    - 从未上传过回执 → 维持空 hint，让 WXML fallback 显示默认占位文案。
  if (isPastDay) {
    if (receiptEverExisted || hasReceiptUrl || hasReceiptNote) {
      return { canShowReceiptImage: false, receiptHint: '回执照片仅送餐当天可查看' };
    }
    return { canShowReceiptImage: false, receiptHint: '' };
  }

  // 3) 当天：骑手已上传回执，但仍在餐期释放时间之前。
  //    提示用户稍后再来，避免误以为"没图"。
  if (isSameDay && hasReceiptUrl && !item.receiptVisible) {
    const releaseText = resolveReleaseTimeText(item.mealPeriod);
    if (releaseText) {
      return {
        canShowReceiptImage: false,
        receiptHint: `配送已完成，图片将于 ${releaseText} 后可见`
      };
    }
  }

  // 4) 当天 / 其他：从未上传过回执，提示空状态。
  return { canShowReceiptImage: false, receiptHint: '' };
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
