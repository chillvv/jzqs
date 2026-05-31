const test = require('node:test');
const assert = require('node:assert/strict');

const {
  buildHomeValueProps,
  buildProfileGuestCard,
  buildInlineAuthSheet
} = require('../utils/customer-entry');

test('customer entry helper exposes value-first copy', () => {
  assert.deepEqual(buildHomeValueProps(), ['明日预订', '营养搭配', '商家自配', '会员餐次']);
  assert.equal(buildProfileGuestCard().title, '确认你的会员手机号');
  assert.equal(buildInlineAuthSheet('order').primaryAction, '微信一键确认');
});
