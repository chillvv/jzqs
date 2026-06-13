const { request } = require('../../utils/request');
const { buildTodayCards } = require('../../utils/today-helpers');

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
    cards: []
  },
  async onShow() {
    const app = getApp();
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight
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
