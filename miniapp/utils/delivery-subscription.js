const { request } = require('./request');

const DELIVERY_TEMPLATE_ID = 'DCpNx6852oVCXO83CKuR-uO8WsgvVEDdAaUgwkLNi3s';
const ACCEPTED_DELIVERY_SUBSCRIPTION_RESULTS = ['accept', 'acceptWithAudio', 'acceptWithAlert'];

async function requestDeliverySubscribeAuthorization(options = {}) {
  const { throwOnUnsupported = false } = options;
  if (typeof wx.requestSubscribeMessage !== 'function') {
    if (throwOnUnsupported) {
      throw new Error('当前微信版本不支持订阅消息测试');
    }
    return '';
  }
  const subscribeResult = await new Promise((resolve) => {
    wx.requestSubscribeMessage({
      tmplIds: [DELIVERY_TEMPLATE_ID],
      success: resolve,
      fail() {
        resolve({});
      }
    });
  });
  const acceptResult = typeof subscribeResult[DELIVERY_TEMPLATE_ID] === 'string'
    ? subscribeResult[DELIVERY_TEMPLATE_ID]
    : '';
  return ACCEPTED_DELIVERY_SUBSCRIPTION_RESULTS.includes(acceptResult) ? acceptResult : '';
}

async function sendDeliverySubscribeMessageTest(acceptResult) {
  return request({
    url: '/api/mobile/customer/subscribe-message/test-send',
    method: 'POST',
    header: { 'content-type': 'application/json' },
    data: {
      templateId: DELIVERY_TEMPLATE_ID,
      acceptResult
    }
  });
}

async function saveOrderDeliverySubscription(orderIds, acceptResult) {
  if (!Array.isArray(orderIds) || !orderIds.length || !acceptResult) {
    return;
  }
  await Promise.all(orderIds.map((orderId) => request({
    url: `/api/mobile/customer/orders/${orderId}/delivery-subscription`,
    method: 'POST',
    header: { 'content-type': 'application/json' },
    data: {
      templateId: DELIVERY_TEMPLATE_ID,
      acceptResult
    }
  }).catch(() => null)));
}

module.exports = {
  DELIVERY_TEMPLATE_ID,
  ACCEPTED_DELIVERY_SUBSCRIPTION_RESULTS,
  requestDeliverySubscribeAuthorization,
  sendDeliverySubscribeMessageTest,
  saveOrderDeliverySubscription
};
