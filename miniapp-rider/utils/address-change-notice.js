/**
 * 后台改址提醒（骑手端）。
 *
 * 为什么需要：商家后台改地址会静默改写/撤销订单的派单快照，骑手端此前收到 dispatch.* 只做
 * 「静默刷新列表」，骑手完全察觉不到，仍按旧地址继续送 → 送错单（2026-09 商家反馈）。
 * 因此收到 dispatch.order.address.changed 时必须显式弹窗 + 震动。
 *
 * 聚合：地址簿改址可能一次影响同一骑手的多单，事件会连续到达，
 * 这里做短延时合并成一次弹窗，避免连着弹好几个框；同一单只提醒一次。
 */
const ADDRESS_CHANGED_EVENT = 'dispatch.order.address.changed';
const MERGE_DELAY_MS = 400;

let pendingNotices = [];
let mergeTimer = null;
let notifiedOrderIds = {};

function isAddressChangeEvent(message) {
  return Boolean(message && message.eventType === ADDRESS_CHANGED_EVENT);
}

function buildFallbackText(payload) {
  const customerName = String((payload && payload.customerName) || '').trim();
  const addressText = String((payload && payload.addressText) || '').trim();
  const who = customerName ? '客户「' + customerName + '」' : '有客户';
  if (!addressText) {
    return who + '的配送地址已变更，请查看订单最新地址。';
  }
  return who + '的配送地址已变更为「' + addressText + '」，请按最新地址配送。';
}

function buildModalContent(notices) {
  if (notices.length === 1) {
    return notices[0].text;
  }
  const lines = notices.map((item, index) => (index + 1) + '. ' + item.text);
  return '共 ' + notices.length + ' 单配送地址有变更，请逐单核对：\n' + lines.join('\n');
}

function flush() {
  mergeTimer = null;
  const notices = pendingNotices;
  pendingNotices = [];
  if (notices.length === 0) {
    return;
  }
  try {
    wx.vibrateShort({ fail: () => {} });
  } catch (e) {
    // 部分机型/基础库不支持震动，忽略即可，不影响弹窗提醒
  }
  wx.showModal({
    title: '配送地址已变更',
    content: buildModalContent(notices),
    showCancel: false,
    confirmText: '知道了'
  });
}

/**
 * 把实时消息里的改址提醒排入队列（非改址事件、同一单重复到达会被忽略）。
 * @returns {boolean} 是否真的排入了提醒
 */
function enqueue(message) {
  if (!isAddressChangeEvent(message)) {
    return false;
  }
  const payload = message.payload || {};
  const orderId = payload.orderId;
  if (orderId != null) {
    if (notifiedOrderIds[orderId]) {
      return false;
    }
    notifiedOrderIds[orderId] = true;
  }
  pendingNotices.push({
    orderId: orderId,
    text: String(payload.noticeText || '').trim() || buildFallbackText(payload)
  });
  if (mergeTimer) {
    clearTimeout(mergeTimer);
  }
  mergeTimer = setTimeout(flush, MERGE_DELAY_MS);
  return true;
}

/** 退出登录/切换骑手时清空提醒记录，避免新骑手被旧提醒打扰。 */
function reset() {
  if (mergeTimer) {
    clearTimeout(mergeTimer);
    mergeTimer = null;
  }
  pendingNotices = [];
  notifiedOrderIds = {};
}

module.exports = {
  ADDRESS_CHANGED_EVENT,
  isAddressChangeEvent,
  enqueue,
  reset
};
