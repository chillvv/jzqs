// 骑手端新手演示模式：临时沙盒假数据（绝不落库、绝不调真实接口）
const STORAGE_KEY = 'demo_active_r1';

function isActive() {
  try {
    return wx.getStorageSync(STORAGE_KEY) === true;
  } catch (e) {
    return false;
  }
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

// 队列假数据：两笔待配送订单（复用页面自身的归一化逻辑渲染）
function getMockQueueItems() {
  return [
    {
      batchItemId: 900001,
      mealSlotOrderId: 900001,
      mealPeriod: 'LUNCH',
      itemStatus: 'PENDING',
      customerName: '演示客户·小王',
      quantity: 1,
      deliveryAddress: '演示路 1 号 演示小区 3 栋 502',
      buildingInfo: '演示小区 3 栋',
      customerPhone: '138****0000',
      note: '-',
      hasAttentionMark: false,
      referenceImageUrl: ''
    },
    {
      batchItemId: 900002,
      mealSlotOrderId: 900002,
      mealPeriod: 'LUNCH',
      itemStatus: 'PENDING',
      customerName: '演示客户·小李',
      quantity: 2,
      deliveryAddress: '演示路 8 号 阳光公寓 B 座 1203',
      buildingInfo: '阳光公寓 B 座',
      customerPhone: '139****0000',
      note: '放门口即可',
      hasAttentionMark: true,
      referenceImageUrl: ''
    }
  ];
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
  getMockQueueItems,
  getMockOrderDetail
};
