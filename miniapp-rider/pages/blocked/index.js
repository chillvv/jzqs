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
    if (target && target !== '/pages/blocked/index') {
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
      if (target && target !== '/pages/blocked/index') {
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
      title: '账号已停用',
      content: '请联系老板确认当前骑手账号是否需要重新启用。',
      showCancel: false
    });
  },
  logout() {
    getApp().logoutRider();
  }
});
