// 新手演示模式：临时沙盒假数据（绝不落库、绝不调真实接口）
// 仅在首次注册用户走新手指引时启用，用户退出演示即清空。
const STORAGE_KEY = 'demo_active_v1';

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
  // 退出演示后，用真实数据重载当前页面
  const pages = getCurrentPages();
  const cur = pages[pages.length - 1];
  if (!cur) return;
  if (typeof cur.loadOrderData === 'function') cur.loadOrderData();
  else if (typeof cur.loadQueue === 'function') cur.loadQueue();
  else if (typeof cur.loadOrders === 'function') cur.loadOrders();
  else if (typeof cur.loadPageData === 'function') cur.loadPageData();
  else if (typeof cur.loadOrderDetail === 'function') {
    cur.loadOrderDetail(cur.data.order && cur.data.order.batchItemId, cur.data.order && cur.data.order.mealSlotOrderId);
  }
}

function tomorrowDate() {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function displayDate(t) {
  return t.slice(5).replace('-', '.');
}

// 点餐页假数据：菜单 + 地址 + 可下单状态
function getMockOrderPageData() {
  const serveDate = tomorrowDate();
  const lunch = {
    id: 'demo-lunch',
    name: '香煎鸡胸藜麦碗',
    mealPeriod: 'LUNCH',
    price: 28,
    description: '高蛋白低脂，适合工作日午餐'
  };
  const dinner = {
    id: 'demo-dinner',
    name: '番茄牛腩糙米饭',
    mealPeriod: 'DINNER',
    price: 32,
    description: '慢炖牛腩配糙米，饱腹又营养'
  };
  const home = {
    customerId: 'demo',
    defaultUserRemark: '',
    remainingMeals: 30,
    nickname: '演示用户'
  };
  const addresses = [
    {
      id: 'demo-addr',
      addressLine: '演示路 1 号 演示小区 3 栋 502',
      contactName: '演示收餐人',
      contactPhone: '138****0000',
      isDefault: true
    }
  ];
  return {
    serveDate,
    serveDateText: displayDate(serveDate),
    home,
    lunchItem: lunch,
    dinnerItem: dinner,
    menuItems: [lunch, dinner],
    addresses
  };
}

// 首页假数据：本周菜单
function getMockHomeData() {
  const home = {
    orderingEnabled: true,
    bannerImages: [],
    popupAnnouncementEnabled: false,
    popupAnnouncementContent: ''
  };
  const rangeText = '08.10 - 08.16';
  const weekdays = ['周一', '周二', '周三'];
  const weekCards = weekdays.map((w, i) => ({
    slotStatus: 'ACTIVE',
    weekdayLabel: w,
    shortDate: `0${i + 1}.10`,
    serveDate: `2026-08-${String(i + 10).padStart(2, '0')}`,
    items: [
      {
        mealPeriod: 'LUNCH',
        dishItems: ['香煎鸡胸藜麦碗', '清炒时蔬', '糙米饭'],
        merchantNote: '-',
        totalCalories: 520
      },
      {
        mealPeriod: 'DINNER',
        dishItems: ['番茄牛腩糙米饭', '凉拌黄瓜', '紫薯'],
        merchantNote: '-',
        totalCalories: 610
      }
    ]
  }));
  return { home, rangeText, weekCards };
}

// 订单列表假数据（已映射好的展示结构，避免依赖 order-list 内部逻辑）
function getMockOrderDisplay() {
  return {
    id: 'demo-order-1',
    serveDateText: '08.10',
    periodText: '午餐',
    statusText: '待配送',
    statusClass: 'pending',
    mealName: '香煎鸡胸藜麦碗',
    quantity: 1,
    mealDetail: '香煎鸡胸藜麦碗（高蛋白低脂）',
    deliveryAddress: '演示路 1 号 演示小区 3 栋 502',
    riderPhone: '',
    note: '-',
    merchantNote: '-',
    canCancel: false,
    canApplyAftersale: false,
    supportRefundStage: '',
    isAftersaleProcessing: false,
    canViewReceipt: false,
    changeAddressMode: '',
    orderPrimaryActionText: '订单详情',
    afterSaleStatus: ''
  };
}

module.exports = {
  isActive,
  start,
  end,
  tomorrowDate,
  displayDate,
  getMockOrderPageData,
  getMockHomeData,
  getMockOrderDisplay
};
