const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ordersDir = path.join(__dirname, '..', 'pages', 'orders');
const ordersWxml = fs.readFileSync(path.join(ordersDir, 'index.wxml'), 'utf8');
const ordersWxss = fs.readFileSync(path.join(ordersDir, 'index.wxss'), 'utf8');
const ordersJs = fs.readFileSync(path.join(ordersDir, 'index.js'), 'utf8');

test('orders page keeps actions visible without extra lifecycle guidance', () => {
  assert.match(ordersWxml, /地址/);
  assert.match(ordersWxml, /取消预订/);
  assert.match(ordersWxml, /申请售后/);
  assert.doesNotMatch(ordersWxml, /Order History/);
  assert.doesNotMatch(ordersWxml, /状态说明/);
  assert.doesNotMatch(ordersWxml, /guidanceText/);
});

test('orders page always exposes a refund entry for undelivered orders', () => {
  assert.match(ordersWxml, /supportRefundStage/);
  assert.match(ordersWxml, /申请退款/);
  assert.match(ordersJs, /requestSupportRefund/);
});

test('quantity sits beside the meal name instead of taking its own row', () => {
  assert.match(ordersWxml, /class="order-qty"[^>]*>×\{\{item\.quantity\}\}/);
  assert.doesNotMatch(ordersWxml, /detail-label">份数/);
});

test('order card typography is balanced between meal name and detail rows', () => {
  const mealNameSize = /\.order-meal-name\s*\{[^}]*font-size:\s*(\d+)rpx/.exec(ordersWxss);
  const detailRowSize = /\.detail-row\s*\{[^}]*font-size:\s*(\d+)rpx/.exec(ordersWxss);
  assert.ok(mealNameSize && detailRowSize);
  const mealName = Number(mealNameSize[1]);
  const detailRow = Number(detailRowSize[1]);
  assert.ok(detailRow >= 24, '地址等信息不能小到看不清');
  assert.ok(mealName - detailRow <= 10, '菜名不应该大得像标题');
});

test('order status pill carries a distinct colour per state', () => {
  ['pending', 'delivered', 'refunded', 'cancelled', 'aftersale', 'rejected'].forEach((state) => {
    assert.match(ordersWxss, new RegExp(`\\.order-status\\.${state}`), `缺少 ${state} 状态配色`);
  });
  assert.doesNotMatch(ordersWxss, /\.order-status\.dispatching/);
});
