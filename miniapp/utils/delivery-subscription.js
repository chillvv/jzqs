const { request } = require('./request');

const DELIVERY_TEMPLATE_ID = 'Od1mOKtl8DPnP0-mVyKKtP4HSYyk3sPbGazcHXZntEs';
// 晚餐取餐提醒使用独立模板：微信一次性订阅按「模板」计额度，双餐段若共用同一模板，
// 一次弹窗只拿到 1 条额度却要发 2 条通知，后送达的那一餐必然被微信拒绝(43101)。
const DELIVERY_DINNER_TEMPLATE_ID = 'Od1mOKtl8DPnP0-mVyKKtGYeRd5RVDcCV40MrW8lBmU';
const NIGHTLY_TEMPLATE_ID = 'gNYZT0Nu18WbkIbgX23zD-fF2h1Gt_-6E3EsWoJCLkQ';
const DELIVERY_ACCEPT_CACHE_KEY = 'delivery_subscribe_accept_cache';
const ACCEPTED_DELIVERY_SUBSCRIPTION_RESULTS = ['accept', 'acceptWithAudio', 'acceptWithAlert'];

function isAccepted(result) {
  return ACCEPTED_DELIVERY_SUBSCRIPTION_RESULTS.includes(result);
}

/**
 * 一次弹窗申请多个订阅模板，返回每个模板各自的授权结果。
 * @param {string[]} [tmplIds] 本次要申请的模板（取餐午餐/晚餐 + 每晚提醒）；不传则默认「午餐 + 每晚」
 * @returns {{accepted: Object<string,string>, delivery: string, deliveryDinner: string, nightly: string}}
 *   accepted 为「模板 ID -> 授权结果」映射，供落库时按订单餐段取对应模板。
 */
async function requestCombinedSubscribeAuthorization(tmplIds, options = {}) {
  const { throwOnUnsupported = false } = options;
  const ids = Array.isArray(tmplIds) && tmplIds.length
    ? tmplIds.filter(Boolean)
    : [DELIVERY_TEMPLATE_ID, NIGHTLY_TEMPLATE_ID];
  if (typeof wx.requestSubscribeMessage !== 'function') {
    if (throwOnUnsupported) {
      throw new Error('当前版本不支持订阅消息');
    }
    return { accepted: {}, delivery: '', deliveryDinner: '', nightly: '' };
  }
  const subscribeResult = await new Promise((resolve) => {
    wx.requestSubscribeMessage({
      tmplIds: ids,
      success: resolve,
      fail() {
        resolve({});
      }
    });
  });
  const accepted = {};
  ids.forEach((id) => {
    const value = subscribeResult[id];
    if (typeof value === 'string' && isAccepted(value)) {
      accepted[id] = value;
    }
  });
  return {
    accepted,
    delivery: accepted[DELIVERY_TEMPLATE_ID] || '',
    deliveryDinner: accepted[DELIVERY_DINNER_TEMPLATE_ID] || '',
    nightly: accepted[NIGHTLY_TEMPLATE_ID] || ''
  };
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

/**
 * 把订单与其对应取餐模板的授权落库。
 * 落库失败必须重试：授权在微信侧已经生效，但记录没写进后端时，该订单送达会因查不到订阅记录
 * 而永久不发通知（历史故障：一次下单两个餐段，其中一个订单的落库请求丢失，整单收不到提醒）。
 * @param {Array<{orderId: number|string, templateId: string, acceptResult: string}>} bindings
 * @returns {Promise<number>} 落库成功的订单数
 */
async function saveOrderDeliverySubscription(bindings) {
  if (!Array.isArray(bindings) || !bindings.length) {
    return 0;
  }
  const targets = bindings.filter((item) => item && item.orderId && item.templateId && item.acceptResult);
  if (!targets.length) {
    return 0;
  }
  const results = await Promise.all(targets.map((item) => saveOneOrderSubscription(item)));
  cacheDeliveryAcceptResult('');
  return results.filter(Boolean).length;
}

/** 单订单落库，失败重试一次；仍失败则打印可检索日志（下单已成，不再打扰用户） */
async function saveOneOrderSubscription({ orderId, templateId, acceptResult }) {
  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      await request({
        url: `/api/mobile/customer/orders/${orderId}/delivery-subscription`,
        method: 'POST',
        header: { 'content-type': 'application/json' },
        // 静默：订单已下单成功，落库失败不应弹错误提示打断用户
        hideErrorToast: true,
        hideLoading: true,
        data: {
          templateId,
          acceptResult
        }
      });
      return true;
    } catch (error) {
      if (attempt === 2) {
        console.error(
          `[delivery-subscription] 取餐订阅落库失败 orderId=${orderId} templateId=${templateId}`,
          error && error.message
        );
        return false;
      }
    }
  }
  return false;
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
 * 判断某个订阅模板是否处于「总是保持」的长期有效状态，且订阅消息总开关已打开。
 * 需同时满足：订阅消息总开关 mainSwitch 打开 + 该模板 itemSettings 值为 'accept'。
 * 总开关是每晚菜单推送能否真正送达用户的前提：总开关关闭时，即使模板是 accept，微信也不会
 * 把消息推给用户，菜单推送会静默失效。因此这里必须校验总开关——总开关关闭时由调用方引导用户
 * 去微信设置里打开总开关，而不是放宽放行（放宽会让菜单推送静默失效）。
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
  DELIVERY_DINNER_TEMPLATE_ID,
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

