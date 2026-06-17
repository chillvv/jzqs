const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const orderPage = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'order', 'index.js'), 'utf8');
const profilePage = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'profile', 'index.js'), 'utf8');
const deliverySubscriptionUtilsPath = path.join(repoRoot, 'miniapp', 'utils', 'delivery-subscription.js');
const deliverySubscriptionUtils = fs.existsSync(deliverySubscriptionUtilsPath)
  ? fs.readFileSync(deliverySubscriptionUtilsPath, 'utf8')
  : '';
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
  deliverySubscriptionUtils.includes('wx.requestSubscribeMessage'),
  true,
  '统一订阅工具应继续请求微信订阅消息授权'
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

const subscribeRequestIndex = orderPage.indexOf('subscriptionResult = await requestDeliverySubscribeAuthorization();');
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
  /let subscriptionResult = ''[\s\S]*requestDeliverySubscribeAuthorization\(\)[\s\S]*catch \(error\)/.test(orderPage),
  true,
  '真实下单链路中订阅授权失败不应打断下单主流程'
);

assert.equal(
  /saveOrderDeliverySubscription\(orderIds, subscriptionResult\);/.test(orderPage),
  true,
  '下单成功后仍应继续尝试保存订阅授权'
);

assert.equal(
  deliverySubscriptionUtils.includes("DCpNx6852oVCXO83CKuR-uO8WsgvVEDdAaUgwkLNi3s"),
  true,
  '统一订阅工具应使用最新的取餐提醒模板 ID'
);

assert.equal(
  deliverySubscriptionUtils.includes('requestDeliverySubscribeAuthorization'),
  true,
  '小程序应抽出正式下单与测试订阅共用的订阅授权工具'
);

assert.equal(
  deliverySubscriptionUtils.includes('saveOrderDeliverySubscription'),
  true,
  '小程序应抽出正式下单保存订阅授权的共用工具'
);

assert.equal(
  profilePage.includes("require('../../utils/delivery-subscription')"),
  true,
  '测试订阅页应改为复用统一的订阅工具'
);

assert.equal(
  orderPage.includes("require('../../utils/delivery-subscription')"),
  true,
  '正式下单页应改为复用统一的订阅工具'
);

assert.equal(
  orderPage.includes('pages/orders/index'),
  true,
  '授权流程完成后仍应跳转订单页'
);

assert.equal(
  /title: '餐次余额不足'[\s\S]*confirmText: '联系商家'[\s\S]*cancelText: '稍后处理'/.test(orderPage),
  true,
  '下单页余额不足提示应统一为服务型续卡引导文案'
);

assert.equal(
  deliverySubscriptionUtils.includes("'acceptWithAudio'"),
  true,
  '统一订阅工具应兼容微信语音提醒订阅返回值 acceptWithAudio'
);

assert.equal(
  mobilePortalService.includes('"acceptWithAudio".equalsIgnoreCase'),
  true,
  '后端应接受微信语音提醒订阅结果 acceptWithAudio'
);

console.log('PASS: 顾客端已接入送达订阅授权链路');
