const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ordersWxml = fs.readFileSync(path.join(__dirname, '..', 'pages', 'orders', 'index.wxml'), 'utf8');

test('orders page keeps actions visible without extra lifecycle guidance', () => {
  assert.match(ordersWxml, /配送地址/);
  assert.match(ordersWxml, /取消订单/);
  assert.match(ordersWxml, /申请售后/);
  assert.doesNotMatch(ordersWxml, /Order History/);
  assert.doesNotMatch(ordersWxml, /状态说明/);
  assert.doesNotMatch(ordersWxml, /订单已生成，商家正在安排配送。/);
});
