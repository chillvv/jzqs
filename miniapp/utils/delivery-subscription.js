const { request } = require('./request');

const DELIVERY_TEMPLATE_ID = 'Od1mOKtl8DPnP0-mVyKKtP4HSYyk3sPbGazcHXZntEs';
const NIGHTLY_TEMPLATE_ID = 'gNYZT0Nu18WbkIbgX23zD-fF2h1Gt_-6E3EsWoJCLkQ';
const DELIVERY_ACCEPT_CACHE_KEY = 'delivery_subscribe_accept_cache';
const ACCEPTED_DELIVERY_SUBSCRIPTION_RESULTS = ['accept', 'acceptWithAudio', 'acceptWithAlert'];

function isAccepted(result) {
  return ACCEPTED_DELIVERY_SUBSCRIPTION_RESULTS.includes(result);
}

/**
 * 合并申请两个订阅模板（送达 + 每晚提醒），一次弹窗。
 * 返回 { delivery, nightly } 各自的授权结果。
 */
async function requestCombinedSubscribeAuthorization(options = {}) {
  const { throwOnUnsupported = false } = options;
  if (typeof wx.requestSubscribeMessage !== 'function') {
    if (throwOnUnsupported) {
      throw new Error('当前版本不支持订阅消息');
    }
    return { delivery: '', nightly: '' };
  }
  const subscribeResult = await new Promise((resolve) => {
    wx.requestSubscribeMessage({
      tmplIds: [DELIVERY_TEMPLATE_ID, NIGHTLY_TEMPLATE_ID],
      success: resolve,
      fail() {
        resolve({});
      }
    });
  });
  const delivery = typeof subscribeResult[DELIVERY_TEMPLATE_ID] === 'string' && isAccepted(subscribeResult[DELIVERY_TEMPLATE_ID])
    ? subscribeResult[DELIVERY_TEMPLATE_ID]
    : '';
  const nightly = typeof subscribeResult[NIGHTLY_TEMPLATE_ID] === 'string' && isAccepted(subscribeResult[NIGHTLY_TEMPLATE_ID])
    ? subscribeResult[NIGHTLY_TEMPLATE_ID]
    : '';
  return { delivery, nightly };
}

async function requestSubscribeAuthorization(templateId, options = {}) {
  const { throwOnUnsupported = false } = options;
  if (typeof wx.requestSubscribeMessage !== 'function') {
    if (throwOnUnsupported) {
      throw new Error('当前版本不支持订阅消息');
    }
    return '';
  }
  const subscribeResult = await new Promise((resolve) => {
    wx.requestSubscribeMessage({
      tmplIds: [templateId],
      success: resolve,
      fail() {
        resolve({});
      }
    });
  });
  const value = typeof subscribeResult[templateId] === 'string' ? subscribeResult[templateId] : '';
  return isAccepted(value) ? value : '';
}

async function requestDeliverySubscribeAuthorization(options = {}) {
  return requestSubscribeAuthorization(DELIVERY_TEMPLATE_ID, options);
}

async function requestNightlySubscribeAuthorization(options = {}) {
  return requestSubscribeAuthorization(NIGHTLY_TEMPLATE_ID, options);
}

function cacheDeliveryAcceptResult(acceptResult) {
  if (acceptResult) {
    wx.setStorageSync(DELIVERY_ACCEPT_CACHE_KEY, acceptResult);
  } else {
    wx.removeStorageSync(DELIVERY_ACCEPT_CACHE_KEY);
  }
}

function getCachedDeliveryAcceptResult() {
  return wx.getStorageSync(DELIVERY_ACCEPT_CACHE_KEY) || '';
}

async function saveOrderDeliverySubscription(orderIds, acceptResult) {
  const result = acceptResult || getCachedDeliveryAcceptResult();
  if (!Array.isArray(orderIds) || !orderIds.length || !result) {
    return;
  }
  await Promise.all(orderIds.map((orderId) => request({
    url: `/api/mobile/customer/orders/${orderId}/delivery-subscription`,
    method: 'POST',
    header: { 'content-type': 'application/json' },
    data: {
      templateId: DELIVERY_TEMPLATE_ID,
      acceptResult: result
    }
  }).catch(() => null)));
  cacheDeliveryAcceptResult('');
}

async function saveNightlySubscription(acceptResult) {
  if (!acceptResult) {
    return;
  }
  await request({
    url: '/api/mobile/customer/nightly-subscription',
    method: 'POST',
    header: { 'content-type': 'application/json' },
    data: {
      templateId: NIGHTLY_TEMPLATE_ID,
      acceptResult
    }
  }).catch(() => null);
}

/** 取消「每晚用餐提醒」订阅，后台将不再向该用户自动推送。 */
async function cancelNightlySubscription() {
  return request({
    url: '/api/mobile/customer/nightly-subscription',
    method: 'DELETE',
    header: { 'content-type': 'application/json' }
  });
}

/**
 * 调试/自测入口：把已同意的模板授权结果提交给后端，由后端真正下发一条订阅测试消息。
 * 后端按 templateId 路由：送达走送达模板、每晚提醒走每晚提醒模板（内容取后台运营设置）。
 * @param {string} templateId 模板 ID
 * @param {string} acceptResult accept / acceptWithAudio / acceptWithAlert
 */
async function sendSubscribeMessageTest(templateId, acceptResult, type) {
  if (!templateId || !acceptResult) {
    throw new Error('缺少订阅模板或授权结果');
  }
  return request({
    url: '/api/mobile/customer/subscribe-message/test-send',
    method: 'POST',
    header: { 'content-type': 'application/json' },
    data: { templateId, acceptResult, type }
  });
}

module.exports = {
  DELIVERY_TEMPLATE_ID,
  NIGHTLY_TEMPLATE_ID,
  DELIVERY_ACCEPT_CACHE_KEY,
  ACCEPTED_DELIVERY_SUBSCRIPTION_RESULTS,
  requestDeliverySubscribeAuthorization,
  requestNightlySubscribeAuthorization,
  requestCombinedSubscribeAuthorization,
  saveOrderDeliverySubscription,
  saveNightlySubscription,
  cancelNightlySubscription,
  sendSubscribeMessageTest,
  cacheDeliveryAcceptResult,
  getCachedDeliveryAcceptResult
};

