// 新手指引（骑手端）：跟随用户真实操作的「可点教程」
// 真实链路：工作台看单 → 点订单卡片进详情 → 拍照/选图 → 填写说明 → 确认送达
// 全程演示沙盒（假数据）：用户真实点按钮，上传/改状态入口被硬拦截，绝不落库。
// 1) 首次登录自动开始；任一步点「跳过」→ 以后不再自动出现。
// 2) 「我的」页「新手指引」可随时手动重看（确认后从工作台开始再走一遍）。
const demo = require('./demo');
const guide = require('./guide');
const auth = require('./auth');

const AUTO_DONE_KEY = 'onboarding_auto_done_v1';
const DISMISS_ALL_KEY = 'guide_dismiss_all_v1';

// 流程阶段：按用户真实操作顺序组织；每步 selector 供蒙层懒定位高亮。
const STAGES = [
  {
    key: 'flow_queue',
    type: 'switchTab',
    url: '/pages/queue/index',
    accent: '#185FA5',
    getSteps: () => ([
      { centered: true, title: '骑手工作台', desc: '这里是你当天要送的订单。花 1 分钟走一遍「进详情 → 传回执」流程，演示订单都是假的，随时可「跳过」。' },
      { selector: '.toggle-section', fallbacks: ['.stats-bento', '.main-content'], title: '午/晚餐切换与统计', desc: '顶部可切换午餐/晚餐，下面能看到总份数、已完成、待配送。' },
      { selector: '.batch-btn-inline', fallbacks: ['.header-action-group'], title: '批量传图', desc: '同一楼栋的多笔订单可批量上传同一张参考图，省时省力（演示环境不会真实上传）。' },
      { selector: '.order-card', fallbacks: ['.orders-list', '.main-content'], title: '点订单进详情', desc: '点任意一张订单卡片进入详情，上传送达照片作为回执。' }
    ])
  },
  {
    key: 'flow_order_detail',
    type: 'navigateTo',
    url: '/pages/order-detail/index?batchItemId=900001&mealSlotOrderId=900001',
    accent: '#185FA5',
    getSteps: () => ([
      { centered: true, title: '送达回执怎么传', desc: '送达后在这页拍一张照片作为回执。下面是完整操作，演示环境不会真实上传。' },
      { selector: '.photo-upload-btn', fallbacks: ['.photo-upload-area', '.proof-section'], title: '拍摄送达照片', desc: '点「点击拍摄送达照片」拍照或从相册选图（演示环境也能真实验选图）。' },
      { selector: '.submit-btn', fallbacks: ['.bottom-action-bar', '.action-bar-buttons'], title: '确认送达', desc: '点底部「确认送达」提交回执，演示环境只弹成功提示，不会真实上传。' },
      { centered: true, title: '新手指引完成', desc: '你已经走完「接单 → 送达回执」的完整流程。想再看，随时到「我的」→「新手指引」。' }
    ])
  }
];

let isRunning = false;
let currentIndex = -1;
let returnTab = '/pages/profile/index';
let flowSession = 0;

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
  const sessionKey = stage.key + '_' + flowSession;
  if (pageCtx && pageCtx.__onbSession === sessionKey) return; // 防重复：同 session 内不重复跑
  if (pageCtx) pageCtx.__onbSession = sessionKey;
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
  try {
    const pages = getCurrentPages();
    pages.forEach((p) => { if (p) p.__onbRun = null; });
  } catch (e) {}
  try { wx.setStorageSync(AUTO_DONE_KEY, 1); } catch (e) {}
  demo.end();
  wx.switchTab({ url: returnTab });
}

function cancelFlow() {
  isRunning = false;
  try {
    const pages = getCurrentPages();
    pages.forEach((p) => { if (p) p.__onbRun = null; });
  } catch (e) {}
  try {
    wx.setStorageSync(AUTO_DONE_KEY, 1);
    wx.setStorageSync(DISMISS_ALL_KEY, 1);
  } catch (e) {}
  demo.end();
  wx.switchTab({ url: returnTab });
}

function startFlow(targetReturnTab) {
  returnTab = targetReturnTab || '/pages/profile/index';
  isRunning = true;
  currentIndex = 0;
  flowSession++;
  demo.start();
  goToStage(0);
}

// 首次登录自动开始（仅已真实登录的骑手；跳过过则不再自动）
function maybeAutoStart() {
  if (isRunning) return;
  if (wx.getStorageSync(AUTO_DONE_KEY)) return;
  if (wx.getStorageSync(DISMISS_ALL_KEY)) return;
  const app = getApp();
  if (!(auth.globalData && auth.globalData.loggedIn && hasValidSession())) return;
  if (!(app && app.globalData && app.globalData.riderRegistered)) return;
  startFlow('/pages/queue/index');
}

// 从「我的」页手动重看
function startFromProfile() {
  if (isRunning) {
    wx.showToast({ title: '指引正在进行中', icon: 'none' });
    return;
  }
  const app = getApp();
  if (!(auth.globalData && auth.globalData.loggedIn && hasValidSession()) || !(app && app.globalData && app.globalData.riderRegistered)) {
    wx.showToast({ title: '开通骑手后即可体验新手指引', icon: 'none' });
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
