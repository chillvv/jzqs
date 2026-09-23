/**
 * 取餐订阅「落库失败 → 下次进入补写」的自愈链路。
 * 背景：授权在微信侧已生效（额度已给），但把「订单 ↔ 模板」写进后端的请求丢了，
 * 该订单送达时查不到订阅记录，顾客永久收不到通知。这条链路必须能自愈，不能静默丢弃。
 */
const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');

const subscriptionPath = path.join(__dirname, '..', 'utils', 'delivery-subscription');

function stubStorage(initial = {}) {
  const store = new Map(Object.entries(initial));
  return {
    setStorageSync(key, value) {
      store.set(key, value);
    },
    getStorageSync(key) {
      return store.has(key) ? store.get(key) : '';
    },
    removeStorageSync(key) {
      store.delete(key);
    },
    store
  };
}

/**
 * 用 wx.request 桩装在真实 request 模块之下（与 miniapp/tests 里其它用例一致的層级），
 * @param {number} failTimes 前 N 次请求按失败处理
 */
function loadModule(storage, failTimes = 0) {
  delete require.cache[require.resolve(subscriptionPath)];
  const calls = [];
  global.getApp = () => ({
    globalData: {
      token: 'test-token',
      apiBaseUrl: 'https://example.test',
      serviceHeaders: {}
    },
    waitForAuth: () => Promise.resolve(),
    handleUnauthorized() {}
  });
  global.wx = {
    request(options) {
      calls.push(options.url);
      if (calls.length <= failTimes) {
        options.fail({ errMsg: 'request:fail network' });
      } else {
        options.success({ statusCode: 200, data: { code: 'OK', data: null } });
      }
      options.complete && options.complete();
    },
    setStorageSync: storage.setStorageSync,
    getStorageSync: storage.getStorageSync,
    removeStorageSync: storage.removeStorageSync,
    showToast() {},
    showLoading() {},
    hideLoading() {}
  };
  const mod = require(subscriptionPath);
  return { mod, calls };
}

test('订阅落库失败时写入待补写队列，不静默丢弃', async () => {
  const storage = stubStorage();
  const { mod } = loadModule(storage, 99);

  const saved = await mod.saveOrderDeliverySubscription([{
    orderId: 900001,
    templateId: mod.DELIVERY_TEMPLATE_ID,
    acceptResult: 'accept'
  }]);

  assert.equal(saved, 0, '网络异常时本次落库应失败');
  assert.notEqual(storage.getStorageSync('delivery_subscribe_pending_bindings'), '', '失败的绑定必须留在待补写队列里');
  assert.match(storage.getStorageSync('delivery_subscribe_pending_bindings'), /900001/);
});

test('下次进入小程序静默补写：成功后队列清空', async () => {
  const storage = stubStorage({
    delivery_subscribe_pending_bindings: JSON.stringify([
      { orderId: 900002, templateId: 'tmpl-lunch', acceptResult: 'accept' },
      { orderId: 900003, templateId: 'tmpl-dinner', acceptResult: 'accept' }
    ])
  });
  const { mod, calls } = loadModule(storage, 0);

  const recovered = await mod.flushPendingDeliverySubscriptions();

  assert.equal(calls.length, 2, '两条待补写都发了请求');
  assert.equal(recovered, 2, '两条待补写都应补成功');
  assert.equal(storage.getStorageSync('delivery_subscribe_pending_bindings'), '', '补写成功后队列应清空');
});

test('补写仍失败时保留队列，等待下次再试', async () => {
  const storage = stubStorage({
    delivery_subscribe_pending_bindings: JSON.stringify([
      { orderId: 900004, templateId: 'tmpl-lunch', acceptResult: 'accept' }
    ])
  });
  const { mod } = loadModule(storage, 99);

  const recovered = await mod.flushPendingDeliverySubscriptions();

  assert.equal(recovered, 0);
  assert.match(storage.getStorageSync('delivery_subscribe_pending_bindings'), /900004/, '仍失败的补写必须继续留在队列里');
});

test('队列为空时补写是安全的空操作', async () => {
  const storage = stubStorage();
  const { mod, calls } = loadModule(storage, 0);

  const recovered = await mod.flushPendingDeliverySubscriptions();

  assert.equal(recovered, 0);
  assert.equal(calls.length, 0, '没有待补写记录时不应发任何请求');
});
