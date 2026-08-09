// 新手指引（顾客端）：跟随用户真实操作的「可点教程」
// 真实链路：首页看菜单 → 点底部「点餐」tab → + 加份 → 去结算 → 确认预订 → 查看预订 → 我的预订看订单
// 全程演示沙盒（假数据）：用户真实点按钮，提交/下单入口被硬拦截，绝不落库。
// 1) 首次登录自动开始；任一步点「跳过」→ 以后不再自动出现。
// 2) 「我的」页「新手指引」可随时手动重看（确认后从首页开始再走一遍）。
const demo = require('./demo');
const guide = require('./guide');
const auth = require('./auth');

const AUTO_DONE_KEY = 'onboarding_auto_done_v1';
const DISMISS_ALL_KEY = 'guide_dismiss_all_v1';

// 流程阶段：按用户真实操作顺序组织；每步 selector 供蒙层懒定位高亮。
const STAGES = [
  {
    key: 'flow_home',
    type: 'switchTab',
    url: '/pages/home/index',
    accent: '#639922',
    getSteps: () => ([
      { centered: true, title: '欢迎使用简知轻食', desc: '花 1 分钟带你真实走一遍点餐流程。演示环境全是假数据，放心点，不会真实下单。随时可「跳过」。' },
      { selector: '.poster-title', fallbacks: ['.menu-section', '.day-card-full', '.home-content'], title: '本周主厨菜单', desc: '每天午/晚吃什么都在这里，每周更新。看完就可以去点餐。' },
      { centered: true, title: '去点餐', desc: '现在点底部导航的「点餐」，我们一起去下单（演示数据，不会真实扣餐次）。' }
    ])
  },
  {
    key: 'flow_order',
    type: 'switchTab',
    url: '/pages/order/index',
    accent: '#639922',
    getSteps: () => ([
      { centered: true, title: '开始点餐', desc: '在这一页选明天的午餐或晚餐。下面按提示真实操作，操作完点「下一步」。' },
      { selector: '.step-btn.plus', fallbacks: ['.stepper', '.meal-item'], title: '点 + 加一份', desc: '点午餐卡片上的 + 号加 1 份，份数累加后底部会出现购物车栏。' },
      { selector: '.btn-checkout', fallbacks: ['.cart-bar', '.cart-info'], title: '去结算', desc: '点底部「去结算」进入确认订单页（演示环境不会真实提交）。' },
      { selector: '.btn-submit', fallbacks: ['.submit-bar', '.checkout-scroll'], title: '确认预订', desc: '核对地址和餐食后，点「确认预订」，会弹出预订成功提示。' },
      { selector: '.success-btn.primary', fallbacks: ['.success-actions', '.success-card'], title: '查看预订', desc: '点弹窗里的「查看预订」，就能在“我的预订”看到这笔订单。' }
    ])
  },
  {
    key: 'flow_orders',
    type: 'navigateTo',
    url: '/pages/orders/index',
    accent: '#639922',
    getSteps: () => ([
      { centered: true, title: '这里就是你的预订', desc: '刚下的单会显示在这，餐食、地址、配送状态都在这张卡片上。' },
      { selector: '.order-card', fallbacks: ['.subpage-card', '.subpage-body'], title: '订单卡片', desc: '每张卡片就是一笔预订，套餐、地址、状态一目了然。' },
      { selector: '.order-status', fallbacks: ['.order-card-header', '.order-card'], title: '配送状态', desc: '右上角是配送状态（待配送/已送达），有变化会实时更新。' },
      { centered: true, title: '新手指引完成', desc: '你已经完整走了一遍点餐流程。以后想再看，随时到「我的」→「新手指引」重看。' }
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
