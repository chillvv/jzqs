// 新手指引工具：控制只弹一次（按当前用户）+ 取页面元素位置 + 状态缺失兜底
const auth = require('./auth');

const PREFIX = 'guide_seen_';
const DISMISS_ALL_KEY = 'guide_dismiss_all_v1';

// 取不到真实元素时用的占位选择器（保证 query 安全返回 null → 走居中兜底）
const PLACEHOLDER = '.gm-guide-placeholder-never';

function getUserId() {
  try {
    return (auth && auth.globalData && auth.globalData.userId) || null;
  } catch (e) {
    return null;
  }
}

function scope() {
  const uid = getUserId();
  return uid ? 'u' + uid : 'device';
}

function shouldShow(key) {
  try {
    if (wx.getStorageSync(DISMISS_ALL_KEY)) return false;
    return !wx.getStorageSync(PREFIX + key + '_' + scope());
  } catch (e) {
    return true;
  }
}

function markShown(key) {
  try {
    wx.setStorageSync(PREFIX + key + '_' + scope(), 1);
  } catch (e) {}
}

// 用户在某一步点了「跳过」→ 所有引导都不再出现
function markAllDismissed() {
  try {
    wx.setStorageSync(DISMISS_ALL_KEY, 1);
  } catch (e) {}
}

function queryRects(pageCtx, selectors) {
  return new Promise((resolve) => {
    if (!pageCtx || !selectors || !selectors.length) {
      resolve([]);
      return;
    }
    const q = (typeof pageCtx.createSelectorQuery === 'function')
      ? pageCtx.createSelectorQuery()
      : wx.createSelectorQuery();
    selectors.forEach((s) => q.select(s).boundingClientRect());
    q.exec((res) => {
      resolve((res || []).map((r) => (r
        ? { top: r.top, left: r.left, width: r.width, height: r.height }
        : null)));
    });
  });
}

// 解析步骤：
// - 主选择器能取到位置 → 高亮该元素
// - 取不到但有 fallback 选择器 → 用 fallback 中第一个命中的
// - 都取不到 → 用「居中说明卡片」兜底（不再挖洞），保证引导不漏步骤、不依赖业务状态
//   （解决：星期天打烊 / 余额不足 / 没单子 时，对应按钮不渲染也能正常展示引导）
async function resolveSteps(pageCtx, candidates) {
  const primary = candidates.map((c) => c.selector || PLACEHOLDER);
  const primaryRects = await queryRects(pageCtx, primary);
  const steps = [];
  for (let i = 0; i < candidates.length; i++) {
    const c = candidates[i];
    let rect = primaryRects[i];
    if (!rect && c.fallbacks && c.fallbacks.length) {
      const fr = await queryRects(pageCtx, c.fallbacks);
      rect = fr.find((r) => r) || null;
    }
    if (rect) {
      steps.push(Object.assign({}, c, { rect, centered: false }));
    } else if (c.centeredFallback !== false) {
      steps.push(Object.assign({}, c, { rect: null, centered: true }));
    }
  }
  return steps;
}

// 在页面数据加载完成后调用；candidates: [{ selector, title, desc, fallbacks?, centered? }]
// opts: { interactive, onSkip, onDone } —— interactive 让蒙层不拦截点击（供演示模式真实点进去）
async function runGuide(pageCtx, key, candidates, accent, opts) {
  opts = opts || {};
  if (!shouldShow(key)) {
    return;
  }
  pageCtx._guideKey = key;
  // 等一帧，确保 setData 已渲染、元素位置可查
  await new Promise((r) => setTimeout(r, 120));
  const steps = await resolveSteps(pageCtx, candidates);
  if (!steps.length) {
    markShown(key);
    return;
  }
  // 先标记已展示，避免重复弹出
  markShown(key);
  const comp = pageCtx.selectComponent('#guideMask');
  if (!comp) {
    return;
  }
  // 用户点「跳过」时，直接让所有引导不再出现（按当前用户记录）
  comp.start(steps, accent, {
    interactive: !!opts.interactive,
    onSkip: (typeof opts.onSkip === 'function') ? opts.onSkip : (() => markAllDismissed()),
    onDone: (typeof opts.onDone === 'function') ? opts.onDone : null
  });
}

module.exports = { shouldShow, markShown, markAllDismissed, queryRects, resolveSteps, runGuide };
