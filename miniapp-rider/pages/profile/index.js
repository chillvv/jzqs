const { shareAppMessage, shareTimeline } = require('../../utils/share');
const onboarding = require('../../utils/onboarding');
const realtime = require('../../utils/realtime');

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
  onShareAppMessage: shareAppMessage,
  onShareTimeline: shareTimeline,
  data: {
    statusBarHeight: 0,
    navBarHeight: 0,
    riderProfile: null,
    riderInfo: null,
    loading: false,
    viewState: 'checking',
    displayName: '骑手游客',
    maskedPhone: '',
    assignArea: '未分配'
  },

  goLoginPage() {
    wx.navigateTo({ url: '/pages/login/index' });
  },

  onShow() {
    this.refreshPage();
    this.startRealtimeSync();

    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 1
      });
    }
  },

  onHide() {
    this.stopRealtimeSync();
  },

  // 订阅实时派单事件：后台换区域/换骑手后，个人中心的「负责区域」等信息秒更新
  startRealtimeSync() {
    this.stopRealtimeSync();
    this._unsubscribeRealtime = realtime.subscribe((message) => {
      if (!message || !message.eventType || !String(message.eventType).startsWith('dispatch.')) {
        return;
      }
      this.refreshPage({ silent: true });
    });
  },

  stopRealtimeSync() {
    if (this._unsubscribeRealtime) {
      this._unsubscribeRealtime();
      this._unsubscribeRealtime = null;
    }
  },

  async refreshPage(options = {}) {
    const { silent = false } = options;
    const app = getApp();
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight
    });

    await app.waitForRiderAuth();
    if (app.globalData.riderRegistered) {
      if (!silent) {
        this.setData({ loading: true });
      }
      try {
        await app.refreshRiderProfile();
      } catch (_) {
        // Keep cached state when the network briefly fails.
      } finally {
        if (!silent) {
          this.setData({ loading: false });
        }
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
      maskedPhone: viewState === 'guest' ? '' : maskPhone(riderProfile.phone),
      assignArea: app.globalData.assignArea || '未分配'
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

  onNewbieGuideTap() {
    wx.showModal({
      title: '新手指引',
      content: '将用演示数据带你完整走一遍「工作台 → 上传送达回执」的流程，约 1 分钟。确认开始吗？',
      confirmText: '确认开始',
      success: (res) => {
        if (res.confirm) {
          onboarding.startFromProfile();
        }
      }
    });
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
        await getApp().logoutRider();
        wx.showToast({ title: '已退出', icon: 'success' });
        this.refreshPage();
      }
    });
  }
});
