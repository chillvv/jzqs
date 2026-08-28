const assert = require('node:assert/strict');

const {
  mapOrderForDisplay,
  resolveOrderSourceText
} = require('../utils/order-list');

const baseOrder = {
  id: 1,
  serveDate: '2026-05-29',
  mealPeriod: 'LUNCH',
  mealName: '香煎鸡胸肉套餐',
  mealDetail: '香煎鸡胸肉+时蔬',
  merchantNote: '-',
  note: '-',
  deliveryAddress: '高新区科技园A座8层',
  status: 'PENDING_DISPATCH',
  receiptUrl: '',
  receiptNote: '',
  deliveredAt: '',
  receiptVisible: false,
  afterSaleOpen: false,
  afterSaleStatus: '',
  afterSaleType: ''
};

const backendOrder = mapOrderForDisplay({
  ...baseOrder,
  source: 'BACKEND'
});

assert.equal(backendOrder.sourceText, '后台代下单');
assert.equal(resolveOrderSourceText('BACKEND'), '后台代下单');

const miniappOrder = mapOrderForDisplay({
  ...baseOrder,
  id: 2,
  source: 'MINIAPP'
});

assert.equal(miniappOrder.sourceText, '自主下单');

const subscriptionOrder = mapOrderForDisplay({
  ...baseOrder,
  id: 3,
  source: 'SUBSCRIPTION'
});

assert.equal(subscriptionOrder.sourceText, '固定订餐');
assert.equal(subscriptionOrder.canViewReceipt, false);

const refundedOrder = mapOrderForDisplay({
  ...baseOrder,
  id: 4,
  status: 'REFUNDED'
});

assert.equal(refundedOrder.canViewReceipt, false);

const deliveredOrder = mapOrderForDisplay({
  ...baseOrder,
  id: 5,
  status: 'DELIVERED',
  deliveredAt: '2026-05-29 11:30:00'
});

assert.equal(deliveredOrder.canViewReceipt, true);
assert.equal(deliveredOrder.orderPrimaryActionText, '订单详情');
assert.equal(deliveredOrder.orderMetaText, '自主下单');
assert.equal(deliveredOrder.statusText, '已送达');
assert.equal(deliveredOrder.statusClass, 'delivered');

const pendingOrder = mapOrderForDisplay({
  ...baseOrder,
  id: 6,
  source: 'MINIAPP'
});

assert.equal(pendingOrder.orderPrimaryActionText, '订单详情');
assert.equal(pendingOrder.orderMetaText, '自主下单');

// 后台已排单的订单，对用户仍然只是"待配送"
const dispatchingOrder = mapOrderForDisplay({
  ...baseOrder,
  id: 7,
  source: 'MINIAPP',
  status: 'DISPATCHING'
});

assert.equal(dispatchingOrder.statusText, '待配送');
assert.equal(dispatchingOrder.statusClass, 'pending');
assert.equal(dispatchingOrder.customerStatus, 'PENDING_DISPATCH');

// 已退款用红色而不是跟"已取消"混在一起的灰色
assert.equal(refundedOrder.statusText, '已退款');
assert.equal(refundedOrder.statusClass, 'refunded');

// 售后中/售后未通过有独立配色
const aftersaleOrder = mapOrderForDisplay({
  ...baseOrder,
  id: 8,
  afterSaleStatus: 'PROCESSING'
});
assert.equal(aftersaleOrder.statusClass, 'aftersale');

const rejectedOrder = mapOrderForDisplay({
  ...baseOrder,
  id: 9,
  afterSaleStatus: 'REJECTED'
});
assert.equal(rejectedOrder.statusClass, 'rejected');

console.log('order-list tests passed');
