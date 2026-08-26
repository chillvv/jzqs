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
 * 把微信侧真实订阅状态回传后端同步，用于纠正后端快照与微信侧真实状态的不一致。
 * enabled=true 恢复 AUTHORIZED（用户在微信设置里重新开启后），false 置为 CANCELLED（用户已关闭）。
 * 前端每次进入下单页时调用，静默同步，失败不阻断流程。
 */
async function syncNightlySubscription(enabled) {
  return request({
    url: '/api/mobile/customer/nightly-subscription/sync',
    method: 'PUT',
    header: { 'content-type': 'application/json' },
    data: {
      enabled: !!enabled,
      templateId: NIGHTLY_TEMPLATE_ID
    },
    // 静默同步：不弹全局 loading，失败也不弹错误提示，避免进入页面时闪烁/打扰
    hideLoading: true,
    hideErrorToast: true
  }).catch(() => null);
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

/**
 * 实时查询微信侧订阅消息的真实授权状态（wx.getSetting withSubscriptions）。
 * 这是唯一能反映「用户当前是否真的还开着订阅」的依据：后端落库状态只是历史快照，
 * 用户可随时在微信设置里关闭订阅，微信不会回调后端，因此下单前必须用本函数实时校验，
 * 否则会出现「库里还是已授权、实际已被用户关闭」的假成功。
 * 返回 { supported, mainSwitch, itemSettings }。
 */
async function querySubscribeAuthorization() {
  if (typeof wx.getSetting !== 'function') {
    return { supported: false, mainSwitch: false, itemSettings: {} };
  }
  return new Promise((resolve) => {
    wx.getSetting({
      withSubscriptions: true,
      success(res) {
        const setting = (res && res.subscriptionsSetting) || {};
        resolve({
          supported: true,
          mainSwitch: setting.mainSwitch === true,
          itemSettings: setting.itemSettings || {}
        });
      },
      fail() {
        resolve({ supported: false, mainSwitch: false, itemSettings: {} });
      }
    });
  });
}

/**
 * 判断某个订阅模板是否处于「总是保持」的长期有效状态。
 * 需同时满足：订阅消息总开关打开 + 该模板 itemSettings 值为 'accept'。
 * 用户勾选了「总是保持」后 itemSettings 才会为 'accept'；关闭总开关或单独拒绝该模板都会失效。
 */
function isTemplateLongTermAccepted(setting, templateId) {
  if (!setting || !setting.supported || !setting.mainSwitch) {
    return false;
  }
  const items = setting.itemSettings || {};
  return items[templateId] === 'accept';
}

/**
 * 实时查询某个模板的「总是保持」授权状态，返回详情：
 * - supported=false：微信侧无法查询（极老基础库或调用失败），调用方应降级处理，避免误判；
 * - supported=true 且 accepted=true：确认为「总是保持」长期有效；
 * - supported=true 且 rejected=true：该模板被用户「总是拒绝」或被后台封禁，requestSubscribeMessage 不会再弹出，需引导去设置；
 * - 其余（supported=true、accepted=false、rejected=false、mainSwitch=true）：该模板尚未被设置过，可正常 requestSubscribeMessage 弹窗。
 */
async function queryTemplateSubscribeStatus(templateId) {
  const setting = await querySubscribeAuthorization();
  const itemValue = (setting.itemSettings || {})[templateId];
  return {
    supported: setting.supported,
    mainSwitch: setting.mainSwitch,
    accepted: isTemplateLongTermAccepted(setting, templateId),
    rejected: setting.supported && (itemValue === 'reject' || itemValue === 'ban')
  };
}

/** 实时查询「每晚用餐提醒」模板的「总是保持」授权状态（三态，见 queryTemplateSubscribeStatus）。 */
async function queryNightlySubscribeStatus() {
  return queryTemplateSubscribeStatus(NIGHTLY_TEMPLATE_ID);
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
  syncNightlySubscription,
  sendSubscribeMessageTest,
  cacheDeliveryAcceptResult,
  getCachedDeliveryAcceptResult,
  querySubscribeAuthorization,
  isTemplateLongTermAccepted,
  queryNightlySubscribeStatus
};

