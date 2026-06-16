const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const orderPage = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'order', 'index.js'), 'utf8');
const mobilePortalService = fs.readFileSync(
  path.join(
    repoRoot,
    'backend',
    'src',
    'main',
    'java',
    'com',
    'jzqs',
    'app',
    'mobile',
    'MobilePortalServiceImpl.java'
  ),
  'utf8'
);

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
  orderPage.includes('showDeliverySubscriptionHint'),
  false,
  '订阅授权前不应插入自定义说明弹窗替代微信官方弹窗'
);

const subscribeRequestIndex = orderPage.indexOf('const subscriptionResult = await requestDeliverySubscription();');
const orderSubmitIndex = orderPage.indexOf('const orderResults = await Promise.all(requests);');

assert.notEqual(
  subscribeRequestIndex,
  -1,
  '确认预订点击链路中应直接发起订阅授权请求'
);

assert.notEqual(
  orderSubmitIndex,
  -1,
  '顾客端应继续提交订单请求'
);

assert.equal(
  subscribeRequestIndex < orderSubmitIndex,
  true,
  '应在用户点击确认预订时先触发官方订阅弹窗，再继续提交订单'
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

assert.equal(
  orderPage.includes("'acceptWithAudio'"),
  true,
  '顾客端应兼容微信语音提醒订阅返回值 acceptWithAudio'
);

assert.equal(
  mobilePortalService.includes('"acceptWithAudio".equalsIgnoreCase'),
  true,
  '后端应接受微信语音提醒订阅结果 acceptWithAudio'
);

console.log('PASS: 顾客端已接入送达订阅授权链路');
