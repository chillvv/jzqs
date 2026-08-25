// 骑手端新手演示模式：临时沙盒假数据（绝不落库、绝不调真实接口）
// 两种激活来源，用不同 storage 键区分，避免互相清除：
//  - 引导演示标记（onboarding 流程写入）
//  - 本地造数标记（开发者工具本地调试写入，ensureCleanDemo 不应清除）
const STORAGE_KEY = 'demo_active_r1';
const LOCAL_GEN_KEY = 'demo_local_gen';
const COUNT_KEY = 'demo_queue_count';
const DEFAULT_COUNT = 15;

function isActive() {
  try {
    return wx.getStorageSync(STORAGE_KEY) === true || wx.getStorageSync(LOCAL_GEN_KEY) === true;
  } catch (e) {
    return false;
  }
}

// 仅清除"引导演示"标记（不影响本地造数）
function clearOnboardingDemo() {
  try {
    wx.removeStorageSync(STORAGE_KEY);
  } catch (e) {}
}

// 直接激活演示模式，并可指定要生成的假订单数量（本地调试造数用）
function startWithCount(count) {
  const n = Math.max(1, Math.min(50, parseInt(count, 10) || DEFAULT_COUNT));
  try {
    wx.setStorageSync(LOCAL_GEN_KEY, true);
    wx.setStorageSync(COUNT_KEY, n);
  } catch (e) {}
}

// 仅清除"本地造数"标记
function endLocalGen() {
  try {
    wx.removeStorageSync(LOCAL_GEN_KEY);
  } catch (e) {}
}

function setCount(count) {
  const n = Math.max(1, Math.min(50, parseInt(count, 10) || DEFAULT_COUNT));
  try {
    wx.setStorageSync(COUNT_KEY, n);
  } catch (e) {}
  return n;
}

function getCount() {
  try {
    const n = parseInt(wx.getStorageSync(COUNT_KEY), 10);
    return n > 0 ? n : DEFAULT_COUNT;
  } catch (e) {
    return DEFAULT_COUNT;
  }
}

function start() {
  startWithCount(DEFAULT_COUNT);
}

function end() {
  try {
    wx.removeStorageSync(STORAGE_KEY);
    wx.removeStorageSync(LOCAL_GEN_KEY);
  } catch (e) {}
  const pages = getCurrentPages();
  const cur = pages[pages.length - 1];
  if (!cur) return;
  if (typeof cur.loadQueue === 'function') cur.loadQueue();
  else if (typeof cur.loadOrderDetail === 'function') {
    cur.loadOrderDetail(cur.data.order && cur.data.order.batchItemId, cur.data.order && cur.data.order.mealSlotOrderId);
  }
}

// 队列假数据：按数量生成若干待配送订单（复用页面自身的归一化逻辑渲染）
// 名字/楼栋/备注随机化，方便肉眼区分；午餐晚餐交替分布。
const SURNAMES = ['王', '李', '张', '刘', '陈', '杨', '黄', '赵', '周', '吴', '徐', '孙', '马', '朱', '胡'];
const BUILDINGS = ['演示小区 3 栋', '阳光公寓 B 座', '幸福里 5 号楼', '中央公馆 2 期', '江畔家园 8 栋', '云栖谷 12 栋', '梧桐苑 1 期', '锦绣华庭 6 栋'];
const NOTES = ['放门口即可', '电话联系', '工作日送达', '勿放快递柜', '轻拿轻放', '提前 10 分钟', '-'];
const ATTENTIONS = ['有宠物', '高层电梯', '门禁 1234', '老人收餐', '易撒漏'];

function getMockQueueItems() {
  const count = getCount();
  const items = [];
  for (let i = 0; i < count; i++) {
    const id = 900001 + i;
    const hasAttention = i % 4 === 0;
    const building = BUILDINGS[i % BUILDINGS.length];
    const room = 100 + Math.floor(Math.random() * 2400);
    items.push({
      batchItemId: id,
      mealSlotOrderId: id,
      mealPeriod: i % 2 === 0 ? 'LUNCH' : 'DINNER',
      itemStatus: 'PENDING',
      customerName: `演示·${SURNAMES[i % SURNAMES.length]}${i + 1}号`,
      quantity: (i % 3 === 0) ? 2 : 1,
      deliveryAddress: `演示路 ${i + 1} 号 ${building} ${room}`,
      buildingInfo: building,
      customerPhone: `138****${(1000 + i).toString().slice(-4)}`,
      note: NOTES[i % NOTES.length],
      hasAttentionMark: hasAttention,
      attentionLabel: hasAttention ? ATTENTIONS[i % ATTENTIONS.length] : '',
      referenceImageUrl: ''
    });
  }
  return items;
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
  startWithCount,
  setCount,
  getCount,
  end,
  clearOnboardingDemo,
  endLocalGen,
  getMockQueueItems,
  getMockOrderDetail
};
