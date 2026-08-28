const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('miniapp should not keep orphan auth-demo page files', () => {
  const authDemoDir = path.join(__dirname, '..', 'pages', 'auth-demo');
  assert.equal(fs.existsSync(authDemoDir), false);
});

test('miniapp profile page should expose wired per-template subscribe test entries', () => {
  const profileWxmlPath = path.join(__dirname, '..', 'pages', 'profile', 'index.wxml');
  const profileJsPath = path.join(__dirname, '..', 'pages', 'profile', 'index.js');
  const deliverySubscriptionPath = path.join(__dirname, '..', 'utils', 'delivery-subscription.js');

  // 我的页提供「测试送达订阅」「测试提醒订阅」两个独立按钮，共用 sendSubscriptionTest 处理函数
  const wxml = fs.readFileSync(profileWxmlPath, 'utf8');
  assert.match(wxml, /测试送达订阅/);
  assert.match(wxml, /测试提醒订阅/);
  assert.match(wxml, /bindtap="sendSubscriptionTest"/);
  assert.match(fs.readFileSync(profileJsPath, 'utf8'), /sendSubscriptionTest\(e\)/);
  // 两个模板各自的授权申请函数已接入
  assert.match(fs.readFileSync(deliverySubscriptionPath, 'utf8'), /requestDeliverySubscribeAuthorization/);
  assert.match(fs.readFileSync(deliverySubscriptionPath, 'utf8'), /requestNightlySubscribeAuthorization/);
  // 后端测试下发接口已接入
  assert.match(fs.readFileSync(deliverySubscriptionPath, 'utf8'), /subscribe-message\/test-send/);
  assert.match(fs.readFileSync(deliverySubscriptionPath, 'utf8'), /sendSubscribeMessageTest/);
});
