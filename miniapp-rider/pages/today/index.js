const { buildTodayCards } = require('../../utils/today-helpers');
const taskService = require('../../services/task.service');
const { createWorkbenchDateOptions, formatDateYMD } = require('../../utils/formatter');
const realtime = require('../../utils/realtime');

function buildWorkbenchDateState(selectedDate) {
  const dateOptions = createWorkbenchDateOptions().map((item) => ({
    ...item,
    active: item.value === selectedDate
  }));
  const activeOption = dateOptions.find((item) => item.active) || dateOptions[1];
  return {
    selectedDate: activeOption.value,
    currentDateLabel: activeOption.shortLabel,
    currentDateTitle: activeOption.label,
    dateOptions
  };
}

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
    currentDateLabel: '',
    currentDateTitle: '今天',
    selectedDate: '',
    showDatePicker: false,
    dateOptions: []
  },
  async onShow() {
    const app = getApp();
    const todayDate = formatDateYMD();
    app.resetWorkbenchDate();
    const nextDateState = buildWorkbenchDateState(todayDate);
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight,
      cards: [],
      summary: {
        totalCount: 0,
        deliveredCount: 0,
        remainingCount: 0
      },
      ...nextDateState,
      showDatePicker: false
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
    const todayDate = formatDateYMD();
    if (this.data.selectedDate === todayDate) {
      return false;
    }
    const app = getApp();
    app.resetWorkbenchDate();
    this.setData({
      ...buildWorkbenchDateState(todayDate),
      showDatePicker: false,
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
    const serveDate = this.data.selectedDate || formatDateYMD();
    if (!riderName) {
      wx.showToast({ title: '骑手信息未就绪', icon: 'none' });
      wx.stopPullDownRefresh();
      return;
    }
    this.setData({ loading: true });
    try {
      const summary = await taskService.getTodaySummary(riderName, serveDate);
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
  openDatePicker() {
    this.setData({ showDatePicker: true });
  },
  closeDatePicker() {
    this.setData({ showDatePicker: false });
  },
  async selectWorkbenchDate(e) {
    const { date } = e.currentTarget.dataset;
    if (!date || date === this.data.selectedDate) {
      this.closeDatePicker();
      return;
    }
    const app = getApp();
    app.setWorkbenchDate(date);
    this.setData({
      ...buildWorkbenchDateState(date),
      showDatePicker: false,
      cards: [],
      summary: {
        totalCount: 0,
        deliveredCount: 0,
        remainingCount: 0
      }
    });
    await this.loadSummary();
  },
  openQueue() {
    const app = getApp();
    if (app.getRiderViewState() !== 'active') {
      wx.switchTab({ url: '/pages/profile/index' });
      return;
    }
    app.setWorkbenchDate(this.data.selectedDate || formatDateYMD());
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
