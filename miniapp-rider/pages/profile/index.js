const AGREEMENT_ACCEPTED_KEY = 'miniapp_rider_auth_agreement_accepted_v2';

function maskPhone(phone) {
  const value = String(phone || '').trim();
  if (value.length < 7) {
    return value || '';
  }
  return `${value.slice(0, 3)}****${value.slice(-4)}`;
}

function buildRiderProfile(app) {
  const profile = app.globalData?.riderProfile || {};
  const activeName = app.getActiveRiderName?.() || '';
  return {
    riderName: profile.riderName || activeName || '',
    displayName: profile.displayName || activeName || '骑手伙伴',
    areaCode: profile.areaCode || '',
    completedCount: profile.completedCount || 0,
    phone: profile.phone || '',
    riderStatus: profile.riderStatus || app.globalData?.riderStatus || 'UNAUTHORIZED',
    firstLoginAt: profile.firstLoginAt || '',
    lastLoginAt: profile.lastLoginAt || ''
  };
}

Page({
  data: {
    statusBarHeight: 0,
    navBarHeight: 0,
    riderProfile: null,
    riderInfo: null,
    loading: false,
    viewState: 'checking',
    displayName: '骑手游客',
    maskedPhone: ''
  },

  goLoginPage() {
    wx.navigateTo({ url: '/pages/login/index' });
  },

  onShow() {
    this.refreshPage();

    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 1
      });
    }
  },

  async refreshPage() {
    const app = getApp();
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight
    });

    await app.waitForRiderAuth();
    if (app.globalData.riderRegistered) {
      this.setData({ loading: true });
      try {
        await app.refreshRiderProfile();
      } catch (_) {
        // Keep cached state when the network briefly fails.
      } finally {
        this.setData({ loading: false });
      }
    }

    const viewState = app.getRiderViewState();
    const riderProfile = buildRiderProfile(app);
    const profileName = riderProfile.displayName || riderProfile.riderName || '骑手游客';

    let workStatusText = '正常';
    if (riderProfile.riderStatus === 'DISABLED') workStatusText = '已停用';
    else if (riderProfile.riderStatus === 'PENDING') workStatusText = '审核中';
    else if (riderProfile.riderStatus === 'NOT_FOUND') workStatusText = '未开通';

    this.setData({
      viewState,
      riderProfile,
      riderInfo: {
        name: riderProfile.displayName || riderProfile.riderName || '骑手',
        nameInitial: (riderProfile.displayName || riderProfile.riderName || '骑')[0],
        phone: riderProfile.phone || '',
        workStatus: workStatusText,
        rawStatus: riderProfile.riderStatus,
        todayDeliveredCount: riderProfile.completedCount || 0
      },
      displayName: viewState === 'guest' ? '骑手游客' : (viewState === 'not_found' ? '未开通骑手' : profileName),
      maskedPhone: viewState === 'guest' ? '' : maskPhone(riderProfile.phone)
    });

    wx.stopPullDownRefresh();
  },

  async handleMenuClick(e) {
    const app = getApp();
    await app.waitForRiderAuth();
    const viewState = app.getRiderViewState();
    if (viewState !== this.data.viewState) {
      this.setData({ viewState });
    }

    if (!app.globalData.riderRegistered || viewState === 'guest') {
      wx.showToast({ title: '请先登录/注册', icon: 'none' });
      this.goLoginPage();
      return;
    }

    if (viewState !== 'active') {
      wx.showToast({ title: app.getWorkbenchBlockMessage(), icon: 'none' });
      return;
    }

    const action = e.currentTarget.dataset.action;
    if (action === 'history') {
      wx.showToast({ title: '历史订单开发中', icon: 'none' });
    } else if (action === 'settings') {
      wx.showToast({ title: '设置功能开发中', icon: 'none' });
    }
  },

  onPullDownRefresh() {
    this.refreshPage();
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: async (res) => {
        if (!res.confirm) {
          return;
        }
        try {
          wx.removeStorageSync(AGREEMENT_ACCEPTED_KEY);
        } catch (_) {}
        await getApp().logoutRider();
        wx.showToast({ title: '已退出', icon: 'success' });
        this.refreshPage();
      }
    });
  }
});
