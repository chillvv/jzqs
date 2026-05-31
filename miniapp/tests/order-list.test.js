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
assert.match(deliveredOrder.orderMetaText, /送达时间/);

const pendingOrder = mapOrderForDisplay({
  ...baseOrder,
  id: 6,
  source: 'MINIAPP'
});

assert.equal(pendingOrder.orderPrimaryActionText, '订单详情');
assert.equal(pendingOrder.orderMetaText, '自主下单');

console.log('order-list tests passed');
