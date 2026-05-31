const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const orderWxml = fs.readFileSync(path.join(__dirname, '..', 'pages', 'order', 'index.wxml'), 'utf8');

test('order page keeps checkout flow without extra booking explanation blocks', () => {
  assert.match(orderWxml, /预订明日餐食/);
  assert.match(orderWxml, /确认订单/);
  assert.match(orderWxml, /先确认会员手机号/);
  assert.doesNotMatch(orderWxml, /预订说明/);
  assert.doesNotMatch(orderWxml, /当前展示的是明日餐食/);
  assert.doesNotMatch(orderWxml, /订单状态、送达回执和餐次扣减会在会员页持续同步/);
});
