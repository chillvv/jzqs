const test = require('node:test');
const assert = require('node:assert/strict');

const customerOrderFlow = require('../utils/customer-order-flow');

const { buildWalletHint, buildAftersaleNotice } = customerOrderFlow;

test('customer order flow helper explains lifecycle states', () => {
  assert.equal(buildWalletHint({ walletDelta: -1 }), '本次预订已扣减 1 餐');
  assert.match(buildAftersaleNotice('REFUND'), /退款申请提交后/);
});

test('lifecycle guidance copy is removed in favour of the status pill', () => {
  // 订单卡右上角的状态胶囊已经表达了履约进度，不再需要"订单状态更新中"这类兜底文案
  assert.equal(customerOrderFlow.buildOrderStatusGuidance, undefined);
});
