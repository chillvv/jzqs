const assert = require('node:assert/strict');
const {
  getCheckoutMealLimitMessage,
  canCancelMiniappOrder,
  resolveSupportRefundStage,
  buildSupportRefundNotice
} = require('../utils/order-guards');
const { formatMonthDay, periodLabel, statusClass, statusLabel, normalizeCustomerStatus } = require('../utils/mobile');
const { resolveOrderActions, resolveOrderStatusText } = require('../utils/aftersale');
const { getReceiptDisplayState } = require('../utils/receipt-display');

function mapOrder(item, now = new Date()) {
  const receiptState = getReceiptDisplayState(item, now);
  return {
    ...item,
    serveDateText: formatMonthDay(item.serveDate),
    periodText: periodLabel(item.mealPeriod),
    statusText: statusLabel(item.status),
    statusClass: statusClass(item.status),
    showReceiptImage: receiptState.canShowReceiptImage,
    receiptHint: receiptState.receiptHint
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

// 送餐前一天（serveDate 在未来）全天都能秒退，不再受下单截止 23:00 约束
assert.equal(
  canCancelMiniappOrder({
    status: 'PENDING_DISPATCH',
    serveDate: '2026-05-15',
    now: '2026-05-14T23:00:00'
  }),
  true
);

// 后台已排单/已分配骑手属于内部调度细节，不应该堵住用户在前一天的自助秒退款
assert.equal(
  canCancelMiniappOrder({
    status: 'DISPATCHING',
    serveDate: '2026-05-15',
    now: '2026-05-14T20:00:00'
  }),
  true
);

assert.equal(
  canCancelMiniappOrder({
    status: 'DELIVERED',
    serveDate: '2026-05-15',
    now: '2026-05-14T20:00:00'
  }),
  false
);

// 未送达但错过自助窗口时，必须留一个客服退款出口，并按出餐时间给不同语气
assert.equal(
  resolveSupportRefundStage({
    status: 'PENDING_DISPATCH',
    serveDate: '2026-05-15',
    now: '2026-05-14T20:00:00'
  }),
  ''
);

// 送餐前一天（未来）依然可自助秒退，不再落入客服窗口
assert.equal(
  resolveSupportRefundStage({
    status: 'PENDING_DISPATCH',
    serveDate: '2026-05-15',
    now: '2026-05-14T23:30:00'
  }),
  ''
);

// 送餐当天：不秒退，引导联系商家协商（SAME_DAY）
assert.equal(
  resolveSupportRefundStage({
    status: 'DISPATCHING',
    serveDate: '2026-05-15',
    now: '2026-05-15T08:30:00'
  }),
  'SAME_DAY'
);

assert.equal(
  resolveSupportRefundStage({
    status: 'DISPATCHING',
    serveDate: '2026-05-15',
    now: '2026-05-15T09:00:00'
  }),
  'SAME_DAY'
);

assert.equal(
  resolveSupportRefundStage({
    status: 'DELIVERED',
    serveDate: '2026-05-15',
    now: '2026-05-15T12:00:00'
  }),
  ''
);

assert.match(buildSupportRefundNotice('SAME_DAY').content, /协商/);
assert.match(buildSupportRefundNotice('AFTER_CUTOFF').content, /申请售后/);

// 用户端只呈现两个履约状态：待配送 / 已送达
assert.equal(statusLabel('PENDING_DISPATCH'), '待配送');
assert.equal(statusLabel('DISPATCHING'), '待配送');
assert.equal(statusLabel('DISPATCHED'), '待配送');
assert.equal(statusLabel('DELIVERED'), '已送达');
assert.equal(statusLabel('REFUNDED'), '已退款');
assert.equal(normalizeCustomerStatus('DISPATCHING'), 'PENDING_DISPATCH');
assert.equal(statusClass('DISPATCHING'), 'pending');
assert.equal(statusClass('DELIVERED'), 'delivered');
assert.equal(statusClass('REFUNDED'), 'refunded');
assert.equal(statusClass('CANCELLED'), 'cancelled');

{
  // 使用当天作为送达日 + receiptVisible=false：当天 11:30 释放时间未到
  // 期望提示"图片将于 11:30 后可见"
  // 固定当天 10:00 作为"现在"，避免真实时钟超过 11:30 后 releasedByClock 兜底分支改变结果
  const now = new Date();
  now.setHours(10, 0, 0, 0);
  const tzOffset = now.getTimezoneOffset() * 60000;
  const todayISODate = new Date(now.getTime() - tzOffset).toISOString().split('T')[0];
  const item = mapOrder({
    status: 'DELIVERED',
    userVisibleStatus: 'PENDING_DISPATCH',
    receiptUrl: '/uploads/rider-receipts/1.jpg',
    receiptNote: '已放前台',
    deliveredAt: `${todayISODate} 10:00:00`,
    receiptVisible: false,
    mealPeriod: 'LUNCH',
    serveDate: todayISODate
  }, now);
  assert.equal(item.showReceiptImage, false);
  assert.equal(item.receiptHint, '配送已完成，图片将于 11:30 后可见');
}

// 送餐前一天晚间（已过原下单截单 23:00，但仍是送餐前一天）：仍可自助秒退
assert.deepEqual(
  resolveOrderActions({
    status: 'PENDING_DISPATCH',
    serveDate: '2026-05-15',
    now: '2026-05-14T23:30:00',
    afterSaleOpen: false
  }),
  { canCancel: true, canApplyAftersale: false, actionText: '取消订单' }
);

assert.deepEqual(
  resolveOrderActions({
    status: 'DELIVERED',
    serveDate: '2026-05-15',
    now: '2026-05-15T13:00:00',
    afterSaleOpen: false
  }),
  { canCancel: false, canApplyAftersale: true, actionText: '申请售后' }
);

assert.deepEqual(
  resolveOrderActions({
    status: 'DELIVERED',
    userVisibleStatus: 'PENDING_DISPATCH',
    serveDate: '2026-05-15',
    now: '2026-05-14T22:30:00',
    afterSaleOpen: false
  }),
  { canCancel: true, canApplyAftersale: false, actionText: '取消订单' }
);

assert.equal(
  resolveOrderStatusText({
    status: 'REFUNDED',
    afterSaleStatus: 'COMPLETED'
  }),
  '已退款'
);

console.log('order-guards tests passed');
