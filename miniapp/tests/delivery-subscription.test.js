const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// delivery-subscription / request 只在函数体内访问小程序全局对象，这里注入最小可用桩。
// 微信一次性订阅按「模板」计额度：双餐段必须落到两个不同的取餐模板上，各自才有一条下发额度。
function stubSubscribe(responder) {
  const calls = [];
  global.wx.requestSubscribeMessage = (options) => {
    calls.push(options.tmplIds.slice());
    const result = {};
    options.tmplIds.forEach((id) => {
      const value = responder(id);
      if (value) {
        result[id] = value;
      }
    });
    options.success(result);
  };
  return calls;
}

let requestHandler = null;

global.wx = {
  request(options) {
    if (requestHandler) {
      requestHandler(options);
      return;
    }
    options.success({ statusCode: 200, data: { code: 'OK', data: null } });
  },
  setStorageSync() {},
  getStorageSync() {
    return '';
  },
  removeStorageSync() {},
  showToast() {},
  showLoading() {},
  hideLoading() {}
};

global.getApp = () => ({
  globalData: { token: 'test-token', apiBaseUrl: 'https://example.test', serviceHeaders: {} },
  waitForAuth: () => Promise.resolve(),
  handleUnauthorized() {}
});

const {
  DELIVERY_TEMPLATE_ID,
  DELIVERY_DINNER_TEMPLATE_ID,
  NIGHTLY_TEMPLATE_ID,
  requestCombinedSubscribeAuthorization,
  saveOrderDeliverySubscription
} = require('../utils/delivery-subscription');

test('午餐与晚餐使用不同的取餐模板，保证各自独立计额度', () => {
  assert.ok(DELIVERY_TEMPLATE_ID);
  assert.ok(DELIVERY_DINNER_TEMPLATE_ID);
  assert.notEqual(DELIVERY_TEMPLATE_ID, DELIVERY_DINNER_TEMPLATE_ID);
});

test('双餐段一次弹窗同时申请午餐/晚餐/每晚三个模板', async () => {
  const calls = stubSubscribe(() => 'accept');

  const result = await requestCombinedSubscribeAuthorization([
    DELIVERY_TEMPLATE_ID,
    DELIVERY_DINNER_TEMPLATE_ID,
    NIGHTLY_TEMPLATE_ID
  ]);

  // 只弹一次窗，但三个模板都被申请到（同一模板传多次也只算一条额度，因此必须用不同模板）
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0], [DELIVERY_TEMPLATE_ID, DELIVERY_DINNER_TEMPLATE_ID, NIGHTLY_TEMPLATE_ID]);
  assert.equal(result.delivery, 'accept');
  assert.equal(result.deliveryDinner, 'accept');
  assert.equal(result.nightly, 'accept');
});

test('晚餐模板被拒时不出现在授权结果中，供下单流程拦截', async () => {
  stubSubscribe((id) => (id === DELIVERY_DINNER_TEMPLATE_ID ? 'reject' : 'accept'));

  const result = await requestCombinedSubscribeAuthorization([
    DELIVERY_TEMPLATE_ID,
    DELIVERY_DINNER_TEMPLATE_ID,
    NIGHTLY_TEMPLATE_ID
  ]);

  assert.equal(result.delivery, 'accept');
  assert.equal(result.deliveryDinner, '');
  assert.equal(result.accepted[DELIVERY_DINNER_TEMPLATE_ID], undefined);
  assert.equal(result.nightly, 'accept');
});

test('订阅落库首次失败会重试一次并最终成功', async () => {
  let attempts = 0;
  requestHandler = (options) => {
    attempts += 1;
    if (attempts === 1) {
      options.fail({ errMsg: 'request:fail timeout' });
      return;
    }
    options.success({ statusCode: 200, data: { code: 'OK', data: null } });
  };

  const saved = await saveOrderDeliverySubscription([
    { orderId: 1001, templateId: DELIVERY_TEMPLATE_ID, acceptResult: 'accept' }
  ]);

  assert.equal(attempts, 2);
  assert.equal(saved, 1);
  requestHandler = null;
});

test('订阅落库两次都失败后放弃，不阻塞下单流程', async () => {
  let attempts = 0;
  requestHandler = (options) => {
    attempts += 1;
    options.fail({ errMsg: 'request:fail timeout' });
  };

  const saved = await saveOrderDeliverySubscription([
    { orderId: 1002, templateId: DELIVERY_DINNER_TEMPLATE_ID, acceptResult: 'accept' }
  ]);

  assert.equal(attempts, 2);
  assert.equal(saved, 0);
  requestHandler = null;
});

test('落库入参为空时不发请求', async () => {
  let attempts = 0;
  requestHandler = () => {
    attempts += 1;
  };

  const saved = await saveOrderDeliverySubscription([]);

  assert.equal(attempts, 0);
  assert.equal(saved, 0);
  requestHandler = null;
});

test('下单页按餐段把订单绑定到对应模板后落库', () => {
  const source = fs.readFileSync(path.join(__dirname, '..', 'pages', 'order', 'index.js'), 'utf8');

  assert.match(source, /resolveOrderDeliveryTemplates\(\)/);
  assert.match(source, /requestBindings\.push\(\{ mealPeriod: 'LUNCH', templateId: DELIVERY_TEMPLATE_ID \}\)/);
  assert.match(source, /requestBindings\.push\(\{ mealPeriod: 'DINNER', templateId: DELIVERY_DINNER_TEMPLATE_ID \}\)/);
  assert.match(source, /saveOrderDeliverySubscription\(\[\.\.\.bindingMap\.values\(\)\]\)/);
});

test('下单页不复用 Promise.all，单个餐段失败不连带跳过另一单的订阅落库', () => {
  const source = fs.readFileSync(path.join(__dirname, '..', 'pages', 'order', 'index.js'), 'utf8');

  // 历史故障：午餐/晚餐并发下单时其中一个请求 500（并发撞日订单唯一键），
  // Promise.all 整体 reject 导致两个订单都没有订阅记录，最终"餐到了却没有通知"。
  assert.match(source, /Promise\.allSettled\(requests\)/);
  assert.doesNotMatch(source, /await Promise\.all\(requests\)/);
  assert.match(source, /failedMealPeriods/);
});

test('订阅被"总是拒绝"时引导去微信设置开启，而不是只弹一句 toast 让用户卡死', () => {
  const source = fs.readFileSync(path.join(__dirname, '..', 'pages', 'order', 'index.js'), 'utf8');

  // 必须一次查询全部模板状态：只查「每晚模板」无法判断取餐模板是否被用户「总是拒绝」，
  // 那时微信不会再有授权弹窗，用户会反复点击却始终下不了单（历史卡死问题）。
  assert.match(source, /querySubscribeAuthorization\(\)/);
  assert.doesNotMatch(source, /queryNightlySubscribeStatus/);
  // 取餐模板被拒时必须走「去设置」引导
  assert.match(source, /promptOpenSubscribeSetting\('deliveryRejected'\)/);
  assert.match(source, /deliveryRejected:/);
  // 从设置返回后要重新校验并给出未解决的具体原因
  assert.match(source, /refreshSubscribeSettingFromWx/);
  // 只是本次点了取消（未勾选「总是保持」）时仍走 toast，微信下次还会弹窗，不存在卡死
  assert.match(source, /deniedDeliveryTemplates\.length > 0/);
});
