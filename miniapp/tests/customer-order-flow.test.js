const test = require('node:test');
const assert = require('node:assert/strict');

const {
  buildOrderStatusGuidance,
  buildWalletHint,
  buildAftersaleNotice
} = require('../utils/customer-order-flow');

test('customer order flow helper explains lifecycle states', () => {
  assert.equal(buildOrderStatusGuidance('PENDING_DISPATCH'), '订单已生成，商家正在安排配送。');
  assert.equal(buildWalletHint({ walletDelta: -1 }), '本次预订已扣减 1 餐。');
  assert.match(buildAftersaleNotice('REFUND'), /退款申请提交后/);
});
