// guide-swiper：独立全屏幻灯片式新手指引
// 不依赖任何真实页面，不跳转。从任意页面调用 comp.start(steps) 即可弹出
// 每一步是一张"活"的假数据页面截图 + 高亮框 + 说明卡

const STORAGE_DONE = 'guide_v2_done';
const STORAGE_DISMISS = 'guide_v2_dismiss';

function shouldShow() {
  try {
    if (wx.getStorageSync(STORAGE_DONE)) return false;
    if (wx.getStorageSync(STORAGE_DISMISS)) return false;
    return true;
  } catch (e) { return true; }
}

function resetAll() {
  try {
    wx.removeStorageSync(STORAGE_DONE);
    wx.removeStorageSync(STORAGE_DISMISS);
  } catch (e) {}
}

module.exports = { shouldShow, resetAll };

Component({
  properties: {
    theme: {
      type: Object,
      value: {
        primary: '#b8d060',
        primaryDark: '#92aa40',
        primaryLight: '#f4f7e6',
        bg: '#f5f6f7',
        cardBg: '#ffffff',
        textMain: '#1a1a1a',
        textSub: '#666666',
        textMuted: '#999999',
        border: '#eeeeee',
        accent: '#b8d060',
        accentShadow: 'rgba(184,208,96,0.3)'
      }
    }
  },

  data: {
    visible: false,
    current: 0,
    total: 0,
    _steps: [],
    _entering: false
  },

  methods: {
    // ===== 公开方法：外部调用入口 =====

    start(stepsArr) {
      if (!stepsArr || !stepsArr.length) return;
      // 确保不受之前标记影响
      this.setData({
        _steps: stepsArr,
        total: stepsArr.length,
        current: 0,
        visible: true,
        _entering: true
      });
      // 入场动画完成后清标记
      setTimeout(() => this.setData({ _entering: false }), 400);
      wx.hideTabBar({ animation: true });
    },

    // ===== 导航 =====

    next() {
      if (this.data.current >= this.data.total - 1) {
        this._finish();
        return;
      }
      this.setData({ current: this.data.current + 1 });
    },

    prev() {
      if (this.data.current <= 0) return;
      this.setData({ current: this.data.current - 1 });
    },

    onSwiperChange(e) {
      this.setData({ current: e.detail.current });
    },

    skip() {
      this._dismiss();
    },

    // ===== 内部 =====

    _finish() {
      try { wx.setStorageSync(STORAGE_DONE, true); } catch (e) {}
      this._close();
      this.triggerEvent('done');
    },

    _dismiss() {
      try { wx.setStorageSync(STORAGE_DISMISS, true); } catch (e) {}
      this._close();
      this.triggerEvent('skip');
    },

    _close() {
      this.setData({ visible: false });
      wx.showTabBar({ animation: true });
    }
  }
});
