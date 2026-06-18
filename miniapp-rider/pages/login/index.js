const AGREEMENT_ACCEPTED_KEY = 'miniapp_rider_auth_agreement_accepted_v2';
const auth = require('../../utils/auth');
const authService = require('../../services/auth.service');

function readAgreementAccepted() {
  try {
    return !!wx.getStorageSync(AGREEMENT_ACCEPTED_KEY);
  } catch (_) {
    return false;
  }
}

function persistAgreementAccepted() {
  try {
    wx.setStorageSync(AGREEMENT_ACCEPTED_KEY, true);
  } catch (_) {}
}

Page({
  data: {
    savingProfile: false,
    statusBarHeight: 0,
    navBarHeight: 44,
    profileForm: {
      phoneNumber: ''
    },
    agreementAccepted: false,
    agreementSheetChecked: false,
    showAgreementSheet: false,
    pendingAgreementAction: ''
  },

  onLoad(options = {}) {
    const app = getApp();
    const agreementAccepted = readAgreementAccepted();
    const phoneNumber = String(options.phoneNumber || '').replace(/\D/g, '');
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight,
      agreementAccepted,
      agreementSheetChecked: agreementAccepted,
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

  openAgreement() {
    wx.showModal({
      title: '骑手服务协议',
      content: '为完成骑手登录、接单、回执上传与配送通知，我们会在你使用过程中处理账号标识、手机号、订单与配送信息。继续使用前，请先阅读并同意骑手服务协议。',
      showCancel: false
    });
  },

  openPrivacy() {
    wx.showModal({
      title: '隐私政策',
      content: '为完成骑手登录、接单、回执上传与配送服务，我们会处理手机号、账号标识、订单与配送相关信息。继续使用前，请阅读并同意隐私政策。',
      showCancel: false
    });
  },

  handleAgreementBarTap() {
    this.openAgreementSheet('');
  },

  openAgreementSheet(action = '') {
    this.setData({
      showAgreementSheet: true,
      pendingAgreementAction: action,
      agreementSheetChecked: this.data.agreementAccepted || this.data.agreementSheetChecked
    });
  },

  closeAgreementSheet() {
    this.setData({
      showAgreementSheet: false,
      pendingAgreementAction: '',
      agreementSheetChecked: this.data.agreementSheetChecked
    });
  },

  stopPropagation() {},

  toggleAgreementSheetChecked() {
    const nextChecked = !this.data.agreementSheetChecked;
    this.setData({
      agreementSheetChecked: nextChecked
    });
  },

  ensureAgreementAccepted() {
    if (!this.data.agreementAccepted && !this.data.agreementSheetChecked) {
      wx.showToast({ title: '请先勾选已阅读并同意', icon: 'none' });
      return false;
    }
    if (!this.data.agreementAccepted) {
      persistAgreementAccepted();
    }
    this.setData({
      agreementAccepted: true,
      agreementSheetChecked: true,
      showAgreementSheet: false,
      pendingAgreementAction: ''
    });
    return true;
  },

  handleAgreementPrimaryAction() {
    if (!this.ensureAgreementAccepted()) {
      return;
    }
    if (this.data.pendingAgreementAction === 'submitProfile') {
      this.submitProfile();
    }
  },

  handleSubmitEntry() {
    if (this.data.agreementAccepted) {
      this.submitProfile();
      return;
    }
    this.openAgreementSheet('submitProfile');
  },

  async submitProfile() {
    if (!this.data.agreementAccepted) {
      wx.showToast({ title: '请先阅读并同意协议', icon: 'none' });
      return;
    }
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
