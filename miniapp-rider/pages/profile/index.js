const { resolvePhoneAuthResult, getSubmitProfileError } = require('../../utils/rider-profile-auth');
const auth = require('../../utils/auth');

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
    savingProfile: false,
    wechatLoading: false,
    showAuthPopup: false,
    profileForm: {
      displayName: '',
      phoneNumber: ''
    },
    displayName: '骑手游客',
    maskedPhone: ''
  },
  onShow() {
    this.refreshPage();
    
    // 更新 tabBar 选中状态
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 1
      });
    }
  },
  openAuthPopup() {
    this.setData({ showAuthPopup: true });
  },
  closeAuthPopup() {
    this.setData({ showAuthPopup: false });
  },
  stopPropagation() {
    // 阻止事件冒泡
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
      } catch (error) {
        // 保留缓存状态，避免网络抖动时把页面重置成空白。
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
      maskedPhone: viewState === 'guest' ? '' : maskPhone(riderProfile.phone),
      'profileForm.phoneNumber': app.globalData.riderProfile?.phone || ''
    });
    wx.stopPullDownRefresh();
  },
  handleMenuClick(e) {
    if (this.data.viewState !== 'active') {
      wx.showToast({ title: '请先登录/注册', icon: 'none' });
      this.openAuthPopup();
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
  onPhoneInput(e) {
    this.setData({
      'profileForm.phoneNumber': e.detail.value
    });
  },
  async submitProfile() {
    const phone = this.data.profileForm.phoneNumber.trim();
    
    if (!phone) {
      wx.showToast({ title: '请输入手机号', icon: 'none' });
      return;
    }
    
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '手机号格式不正确', icon: 'none' });
      return;
    }
    
    if (this.data.savingProfile) {
      return;
    }
    
    this.setData({ savingProfile: true });
    
    try {
      const app = getApp();
      
      if (!app || typeof app.loginWithPhone !== 'function') {
        throw new Error('应用未初始化，请重启小程序');
      }
      
      console.log('[登录] 开始登录，手机号:', phone);
      console.log('[登录] API地址:', app.globalData.apiBaseUrl);
      
      await app.loginWithPhone(phone);
      
      wx.showToast({ title: '登录成功', icon: 'success' });
      
      this.setData({
        showAuthPopup: false
      });
      
      this.refreshPage();
    } catch (error) {
      console.error('[登录] 失败:', error);
      
      let errorMsg = '登录失败，请检查手机号是否已开通';
      if (error.message?.includes('无法连接') || error.message?.includes('请求失败')) {
        errorMsg = '无法连接服务器，请检查网络或联系管理员';
      } else if (error.message) {
        errorMsg = error.message;
      }
      
      wx.showToast({ 
        title: errorMsg, 
        icon: 'none',
        duration: 3000
      });
    } finally {
      this.setData({ savingProfile: false });
    }
  },
  
  /**
   * 微信一键登录
   */
  async onWechatLogin(e) {
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      wx.showToast({ title: '获取手机号失败，请重试', icon: 'none' });
      return;
    }

    this.setData({ wechatLoading: true });

    try {
      const code = e.detail.code;
      const app = getApp();
      await auth.bindPhone(code);
      app.syncRiderGlobals();

      wx.showToast({ title: '登录成功', icon: 'success' });
      this.setData({ agreed: false });
      setTimeout(() => this.refreshPage(), 1200);
    } catch (error) {
      wx.showToast({
        title: error.message || '微信登录失败',
        icon: 'none',
        duration: 3000
      });
    } finally {
      this.setData({ wechatLoading: false });
    }
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
      }
    });
  }
});
