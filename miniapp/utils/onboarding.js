// 新手指引（顾客端）：「纯下一步」驱动 —— 用户只点气泡里的「下一步/完成」，
// 系统自动跳页、自动展示假数据、自动框起关键功能。
// 真实链路：首页菜单 → 点餐页（预填数据）→ 我的页（找到预订入口）→ 订单列表 → 完成。
// 注意：底部 tab 栏是微信原生组件，无法用 select 查到，所有跳页都用居中说明 + 自动跳转。
const demo = require('./demo');
const guide = require('./guide');
const auth = require('./auth');

const AUTO_DONE_KEY = 'onboarding_auto_done_v1';
const DISMISS_ALL_KEY = 'guide_dismiss_all_v1';

// 流程阶段：每页若干步骤，最后一步点「完成」自动跳到下一阶段。
const STAGES = [
  {
    key: 'flow_home',
    type: 'switchTab',
    url: '/pages/home/index',
    accent: '#639922',
    getSteps: () => ([
      { centered: true, title: '欢迎使用简知轻食', desc: '花 1 分钟带你走一遍完整点餐流程。演示环境全是假数据，不会真实下单，随时可点「跳过」。' },
      { selector: '.poster-title', fallbacks: ['.menu-section', '.day-card-full', '.home-content'], title: '每周主厨菜单', desc: '每天午/晚吃什么都在这里，每周更新。看完就可以去点餐了。' },
      { centered: true, title: '下一步：点餐页', desc: '首页讲完了。点「完成」，系统自动带你到点餐页面——就像你平时点底部「点餐」tab 一样。' }
    ])
  },
  {
    key: 'flow_order',
    type: 'switchTab',
    url: '/pages/order/index',
    accent: '#639922',
    getSteps: () => ([
      { centered: true, title: '点餐页面', desc: '欢迎来到点餐页——这是你每次下单的主战场。演示数据已预填 1 份午餐，方便你看清每个位置。' },
      { selector: '.meal-item', fallbacks: ['.stepper', '.lunch-selector'], title: '选餐区', desc: '明天午餐和晚餐各一张卡片，每份标了菜名和热量。你可以点 + / − 增减份数。' },
      { selector: '.btn-checkout', fallbacks: ['.cart-bar', '.cart-info'], title: '购物车 & 去结算', desc: '选好后底部会出现购物车栏和「去结算」按钮，汇总你选的餐食。' },
      { centered: true, title: '下一步：查看预订', desc: '下单后去哪看订单？点「完成」带你去「我的」页面找预订入口。' }
    ])
  },
  {
    key: 'flow_profile',
    type: 'switchTab',
    url: '/pages/profile/index',
    accent: '#639922',
    getSteps: () => ([
      { centered: true, title: '我的页面', desc: '这就是你个人的页面。余额、餐数、设置都在这。下单后在这找你的订单记录。' },
      { selector: '.menu-group .list-item', fallbacks: ['.list-title', '.menu-group'], title: '我的预订记录', desc: '点这里就能看到你所有订单的状态——待配送 / 已送达，一目了然。' },
      { centered: true, title: '下一步：订单详情', desc: '点「完成」带你进去看一笔演示订单长什么样。' }
    ])
  },
  {
    key: 'flow_orders',
    type: 'navigateTo',
    url: '/pages/orders/index',
    accent: '#639922',
    getSteps: () => ([
      { centered: true, title: '这里就是你的预订', desc: '刚刚下的单会显示在这儿，餐食、地址、配送状态都在这张卡片上。' },
      { selector: '.order-card', fallbacks: ['.subpage-card', '.subpage-body'], title: '订单卡片', desc: '每一张卡片就是一笔预订，套餐内容、地址、状态一目了然。' },
      { selector: '.order-status', fallbacks: ['.order-card-header', '.order-card'], title: '配送状态', desc: '右上角是配送状态（待配送/已配送），有变化会实时更新。' },
      { centered: true, title: '新手指引完成 🎉', desc: '你已经完整走了一遍点餐流程！想再看随时到「我的」→「新手指引」重看。' }
    ])
  }
];

let isRunning = false;
let currentIndex = -1;
let returnTab = '/pages/home/index';

// 是否有真实登录会话（token 已持久化）。
// 防「openid 已注册但未登录（无 token）」的假注册态误启动引导。
function hasValidSession() {
  try {
    return !!wx.getStorageSync('auth_token');
  } catch (e) {
    return false;
  }
}

function currentStageKey() {
  return (currentIndex >= 0 && currentIndex < STAGES.length) ? STAGES[currentIndex].key : null;
}

// 是否轮到本页讲解：跟随用户真实跳转——
// 用户走到比当前更靠后的阶段页时，直接跟进到该阶段（当前页没讲完也不阻塞）。
function shouldRunStageHere(stageKey) {
  if (!isRunning || !demo.isActive()) return false;
  const idx = STAGES.findIndex((s) => s.key === stageKey);
  if (idx < 0) return false;
  if (idx > currentIndex) currentIndex = idx;
  return idx === currentIndex;
}

function isRunningFlow() {
  return isRunning;
}

// 清理残留的演示态（防止上次流程被强制中断后一直停在假数据）
function ensureCleanDemo() {
  if (demo.isActive() && !isRunning) {
    demo.end();
  }
}

function runCurrentStage(pageCtx) {
  const stage = STAGES[currentIndex];
  if (!stage) return;
  if (pageCtx && pageCtx.__onbRun === stage.key) return; // 防重复触发
  if (pageCtx) pageCtx.__onbRun = stage.key;
  guide.runGuide(pageCtx, stage.key, stage.getSteps(), stage.accent, {
    force: true,
    interactive: true, // 蒙层不拦截点击，用户真实点按钮走流程
    onSkip: cancelFlow,
    onDone: advance
  });
}

function goToStage(index) {
  currentIndex = index;
  const stage = STAGES[index];
  if (stage.type === 'switchTab') {
    wx.switchTab({ url: stage.url });
  } else {
    wx.navigateTo({ url: stage.url });
  }
}

function advance() {
  if (currentIndex + 1 < STAGES.length) {
    goToStage(currentIndex + 1);
  } else {
    finishFlow();
  }
}

function finishFlow() {
  isRunning = false;
  try { wx.setStorageSync(AUTO_DONE_KEY, 1); } catch (e) {}
  demo.end();
  wx.switchTab({ url: returnTab });
}

function cancelFlow() {
  isRunning = false;
  try {
    wx.setStorageSync(AUTO_DONE_KEY, 1);
    wx.setStorageSync(DISMISS_ALL_KEY, 1);
  } catch (e) {}
  demo.end();
  wx.switchTab({ url: returnTab });
}

function startFlow(targetReturnTab) {
  returnTab = targetReturnTab || '/pages/home/index';
  isRunning = true;
  currentIndex = 0;
  demo.start();
  goToStage(0);
}

// 首次登录自动开始（仅已真实登录的用户；跳过过则不再自动）
function maybeAutoStart() {
  if (isRunning) return;
  if (wx.getStorageSync(AUTO_DONE_KEY)) return;
  if (wx.getStorageSync(DISMISS_ALL_KEY)) return;
  if (!(auth.globalData && auth.globalData.loggedIn && hasValidSession())) return;
  startFlow('/pages/home/index');
}

// 从「我的」页手动重看
function startFromProfile() {
  if (isRunning) {
    wx.showToast({ title: '指引正在进行中', icon: 'none' });
    return;
  }
  if (!(auth.globalData && auth.globalData.loggedIn && hasValidSession())) {
    wx.showToast({ title: '登录后即可体验新手指引', icon: 'none' });
    return;
  }
  startFlow('/pages/profile/index');
}

module.exports = {
  maybeAutoStart,
  startFromProfile,
  shouldRunStageHere,
  isRunningFlow,
  ensureCleanDemo,
  runCurrentStage
};
