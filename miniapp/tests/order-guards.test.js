const assert = require('node:assert/strict');
const { getCheckoutMealLimitMessage, canCancelMiniappOrder } = require('../utils/order-guards');
const { formatMonthDay, periodLabel, statusClass, statusLabel } = require('../utils/mobile');
const { resolveOrderActions, resolveOrderStatusText } = require('../utils/aftersale');

function mapOrder(item) {
  const showReceiptImage = Boolean(item.receiptUrl) && Boolean(item.receiptVisible);
  const receiptHint = !showReceiptImage && item.status === 'DELIVERED'
    ? (item.mealPeriod === 'LUNCH' ? '配送已完成，图片将于 11:30 后可见' : '配送已完成，图片将于 17:00 后可见')
    : '';
  return {
    ...item,
    serveDateText: formatMonthDay(item.serveDate),
    periodText: periodLabel(item.mealPeriod),
    statusText: statusLabel(item.status),
    statusClass: statusClass(item.status),
    showReceiptImage,
    receiptHint
  };
}

assert.equal(
  getCheckoutMealLimitMessage({ totalQty: 2, remainingMeals: 1 }),
  '剩余餐次不足，请调整餐食数量后再结算'
);

assert.equal(
  getCheckoutMealLimitMessage({ totalQty: 1, remainingMeals: 1 }),
  ''
);

assert.equal(
  canCancelMiniappOrder({
    status: 'PENDING_DISPATCH',
    serveDate: '2026-05-15',
    now: '2026-05-14T22:59:00'
  }),
  true
);

assert.equal(
  canCancelMiniappOrder({
    status: 'PENDING_DISPATCH',
    serveDate: '2026-05-15',
    now: '2026-05-14T23:00:00'
  }),
  false
);

assert.equal(
  canCancelMiniappOrder({
    status: 'DISPATCHING',
    serveDate: '2026-05-15',
    now: '2026-05-14T20:00:00'
  }),
  false
);

{
  const item = mapOrder({
    status: 'DELIVERED',
    receiptUrl: '/uploads/rider-receipts/1.jpg',
    receiptNote: '已放前台',
    deliveredAt: '2026-05-15 10:00:00',
    receiptVisible: false,
    mealPeriod: 'LUNCH',
    serveDate: '2026-05-15'
  });
  assert.equal(item.showReceiptImage, false);
  assert.match(item.receiptHint, /11:30/);
}

assert.deepEqual(
  resolveOrderActions({
    status: 'PENDING_DISPATCH',
    serveDate: '2026-05-15',
    now: '2026-05-14T23:30:00',
    afterSaleOpen: false
  }),
  { canCancel: false, canApplyAftersale: true, actionText: '申请售后' }
);

assert.equal(
  resolveOrderStatusText({
    status: 'REFUNDED',
    afterSaleStatus: 'COMPLETED'
  }),
  '已退款'
);

console.log('order-guards tests passed');
