const { request } = require('../../utils/request');
const { buildTodayCards } = require('../../utils/today-helpers');
const { formatCurrentDateMMDD } = require('../../utils/formatter');
const realtime = require('../../utils/realtime');

Page({
  data: {
    statusBarHeight: 0,
    navBarHeight: 44,
    loading: false,
    viewState: 'checking',
    summary: {
      totalCount: 0,
      deliveredCount: 0,
      remainingCount: 0
    },
    cards: [],
    refreshInterval: null,
    currentDateLabel: ''
  },
  async onShow() {
    const app = getApp();
    
    // 检查日期是否变更，如变更直接清空缓存重载
    const todayLabel = formatCurrentDateMMDD();
    if (this.data.currentDateLabel && this.data.currentDateLabel !== todayLabel) {
      console.log('日期已变更，清空页面数据');
      this.setData({ cards: [], summary: { totalCount: 0, deliveredCount: 0, remainingCount: 0 } });
    }
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight,
      currentDateLabel: todayLabel
    });
    await app.waitForRiderAuth();
    const viewState = app.getRiderViewState();
    this.setData({ viewState });
    if (viewState !== 'active') {
      this.setData({
        loading: false,
        summary: {
          totalCount: 0,
          deliveredCount: 0,
          remainingCount: 0
        },
        cards: []
      });
      return;
    }
    this.loadSummary();
    this.startAutoRefresh();
    this.startRealtimeSync();
  },

  onHide() {
    this.stopAutoRefresh();
    this.stopRealtimeSync();
  },

  _syncCurrentDateLabel() {
    const todayLabel = formatCurrentDateMMDD();
    if (this.data.currentDateLabel === todayLabel) {
      return false;
    }
    this.setData({
      currentDateLabel: todayLabel,
      cards: [],
      summary: {
        totalCount: 0,
        deliveredCount: 0,
        remainingCount: 0
      }
    });
    return true;
  },

  startAutoRefresh() {
    this.stopAutoRefresh();
    this.data.refreshInterval = setInterval(() => {
      this._syncCurrentDateLabel();
      this.loadSummary();
    }, 8000);
  },

  stopAutoRefresh() {
    if (this.data.refreshInterval) {
      clearInterval(this.data.refreshInterval);
      this.setData({ refreshInterval: null });
    }
  },

  startRealtimeSync() {
    this.stopRealtimeSync();
    this._unsubscribeRealtime = realtime.subscribe((message) => {
      if (!message || !message.eventType || !String(message.eventType).startsWith('dispatch.')) {
        return;
      }
      if (this.data.viewState !== 'active') {
        return;
      }
      this.loadSummary();
    });
  },

  stopRealtimeSync() {
    if (this._unsubscribeRealtime) {
      this._unsubscribeRealtime();
      this._unsubscribeRealtime = null;
    }
  },
  onPullDownRefresh() {
    if (this.data.viewState !== 'active') {
      this.onShow();
      return;
    }
    this.loadSummary();
  },
  async loadSummary() {
    const app = getApp();
    const riderName = app.getActiveRiderName();
    if (!riderName) {
      wx.showToast({ title: '骑手信息未就绪', icon: 'none' });
      wx.stopPullDownRefresh();
      return;
    }
    this.setData({ loading: true });
    try {
      const summary = await request({ url: `/api/mobile/rider/summary?riderName=${encodeURIComponent(riderName)}` });
      const cards = buildTodayCards(summary);
      app.globalData.riderProfile.riderName = summary.riderName || riderName;
      app.globalData.riderProfile.completedCount = summary.deliveredCount || 0;
      this.setData({ summary, cards });
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },
  openQueue() {
    const app = getApp();
    if (app.getRiderViewState() !== 'active') {
      wx.switchTab({ url: '/pages/profile/index' });
      return;
    }
    wx.switchTab({ url: '/pages/queue/index' });
  },

  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack();
      return;
    }
    wx.switchTab({ url: '/pages/queue/index' });
  }
});
