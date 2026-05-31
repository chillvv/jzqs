Page({
  data: {
    riderProfile: null,
    refreshing: false
  },
  async onShow() {
    const app = getApp();
    await app.waitForRiderAuth();
    if (app.canUseWorkbench()) {
      app.openWorkbench();
      return;
    }
    const target = app.resolveRiderEntryPage();
    if (target && target !== '/pages/pending/index') {
      wx.reLaunch({ url: target });
      return;
    }
    this.syncProfile();
  },
  syncProfile() {
    const app = getApp();
    this.setData({
      riderProfile: {
        displayName: app.globalData.riderProfile.displayName || app.getActiveRiderName() || '未命名骑手',
        phone: app.globalData.riderProfile.phone || '',
        riderStatus: app.globalData.riderStatus,
        firstLoginAt: app.globalData.riderProfile.firstLoginAt || '',
        lastLoginAt: app.globalData.riderProfile.lastLoginAt || ''
      }
    });
  },
  async refreshStatus() {
    const app = getApp();
    this.setData({ refreshing: true });
    try {
      await app.refreshRiderProfile();
      if (app.canUseWorkbench()) {
        app.openWorkbench();
        return;
      }
      const target = app.resolveRiderEntryPage();
      if (target && target !== '/pages/pending/index') {
        wx.reLaunch({ url: target });
        return;
      }
      this.syncProfile();
      wx.showToast({ title: '状态已刷新', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message || '刷新失败', icon: 'none' });
    } finally {
      this.setData({ refreshing: false });
    }
  },
  contactMerchant() {
    wx.showModal({
      title: '等待分配',
      content: '请联系老板在后台配送中心为当前骑手分配区域并启用账号。',
      showCancel: false
    });
  },
  logout() {
    getApp().logoutRider();
  }
});
