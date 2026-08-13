const { shareAppMessage, shareTimeline } = require('../../utils/share');
const auth = require('../../utils/auth');
const authService = require('../../services/auth.service');

Page({
  onShareAppMessage: shareAppMessage,
  onShareTimeline: shareTimeline,
  data: {
    savingProfile: false,
    statusBarHeight: 0,
    navBarHeight: 44,
    profileForm: {
      phoneNumber: ''
    }
  },

  onLoad(options = {}) {
    const app = getApp();
    const phoneNumber = String(options.phoneNumber || '').replace(/\D/g, '');
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight,
      'profileForm.phoneNumber': phoneNumber
    });
  },

  onShow() {
    const state = auth.getAuthState();
    if (state.loggedIn && state.registered) {
      wx.switchTab({ url: '/pages/profile/index' });
    }
  },

  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack();
      return;
    }
    wx.switchTab({ url: '/pages/profile/index' });
  },

  onPhoneInput(e) {
    this.setData({
      'profileForm.phoneNumber': String(e.detail.value || '').replace(/\D/g, '')
    });
  },

  handleSubmitEntry() {
    this.submitProfile();
  },

  async submitProfile() {
    const phone = String(this.data.profileForm.phoneNumber || '').trim();
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
      const state = auth.getAuthState();
      if (state.openid || auth.globalData.openid) {
        const response = await authService.bindPhone(
          state.openid || auth.globalData.openid,
          phone,
          '骑手'
        );
        auth.applyAuth(response);
        auth.globalData.ready = true;
        if (typeof app.syncRiderGlobals === 'function') {
          app.syncRiderGlobals();
        }
      } else {
        await app.loginWithPhone(phone);
      }
      wx.showToast({ title: '登录成功', icon: 'success' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/profile/index' });
      }, 800);
    } catch (error) {
      let errorMsg = '登录失败，请检查手机号是否已开通';
      if (error.message && (error.message.includes('无法连接') || error.message.includes('请求失败'))) {
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

  goRegisterPage() {
    const phoneNumber = String(this.data.profileForm.phoneNumber || '').replace(/\D/g, '');
    const query = phoneNumber ? `?phoneNumber=${phoneNumber}` : '';
    wx.navigateTo({ url: `/pages/register/index${query}` });
  }
});
