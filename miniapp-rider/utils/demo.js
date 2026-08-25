// 骑手端新手演示模式：临时沙盒假数据（绝不落库、绝不调真实接口）
// 仅用于「新手指引」引导流程（onboarding 写入 STORAGE_KEY 激活）。
const STORAGE_KEY = 'demo_active_r1';

function isActive() {
  try {
    return wx.getStorageSync(STORAGE_KEY) === true;
  } catch (e) {
    return false;
  }
}

// 仅清除"引导演示"标记
function clearOnboardingDemo() {
  try {
    wx.removeStorageSync(STORAGE_KEY);
  } catch (e) {}
}

function start() {
  try {
    wx.setStorageSync(STORAGE_KEY, true);
  } catch (e) {}
}

function end() {
  try {
    wx.removeStorageSync(STORAGE_KEY);
  } catch (e) {}
  const pages = getCurrentPages();
  const cur = pages[pages.length - 1];
  if (!cur) return;
  if (typeof cur.loadQueue === 'function') cur.loadQueue();
  else if (typeof cur.loadOrderDetail === 'function') {
    cur.loadOrderDetail(cur.data.order && cur.data.order.batchItemId, cur.data.order && cur.data.order.mealSlotOrderId);
  }
}

// 订单详情假数据
function getMockOrderDetail(bid, mid) {
  return {
    batchItemId: bid || 900001,
    mealSlotOrderId: mid || 900001,
    mealPeriod: 'LUNCH',
    mealLabel: '午餐',
    itemStatus: 'PENDING',
    customerName: '演示客户·小王',
    quantity: 1,
    deliveryAddress: '演示路 1 号 演示小区 3 栋 502',
    buildingInfo: '演示小区 3 栋',
    customerPhone: '138****0000',
    customerNote: '演示备注：放门口即可',
    merchantNote: '-',
    addressId: null,
    receiptUrl: '',
    receiptNote: ''
  };
}

module.exports = {
  isActive,
  start,
  end,
  clearOnboardingDemo,
  getMockOrderDetail
};
