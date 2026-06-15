const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const orderPage = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'order', 'index.js'), 'utf8');

assert.equal(
  orderPage.includes('wx.requestSubscribeMessage'),
  true,
  '下单成功后应请求微信订阅消息授权'
);

assert.equal(
  orderPage.includes('/delivery-subscription'),
  true,
  '下单成功后应把授权成功的订单上报后端'
);

assert.equal(
  orderPage.includes("DCpNx6852oVCXO83CKuR-uO8WsgvVEDdAaUgwkLNi3s"),
  true,
  '顾客端应使用最新的取餐提醒模板 ID'
);

assert.equal(
  orderPage.includes('pages/orders/index'),
  true,
  '授权流程完成后仍应跳转订单页'
);

console.log('PASS: 顾客端已接入送达订阅授权链路');
