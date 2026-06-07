const { request } = require('../../utils/request');
const { maskPhone } = require('../../utils/mobile');
const { getSubmitProfileError } = require('../../utils/profile-auth');
const { ensurePhonePrivacyPermission, getPhonePrivacyErrorMessage } = require('../../utils/privacy-auth');
const auth = require('../../utils/auth');

function displayName(name) {
  if (!name || name.startsWith('微信用户-') || name.startsWith('待完善-')) {
    return '微信用户';
  }
  return name;
}

function isPlaceholderCustomerName(name) {
  const value = String(name || '').trim();
  return value.startsWith('微信用户-') || value.startsWith('待完善-');
}

function shouldStartRegister(error) {
  const message = String(error && error.message || '').trim();
  return message.includes('未注册') || message.includes('请先注册');
}

function applyCustomerAuthResult(result) {
  if (!result) {
    return;
  }
  auth.applyAuthState(result);
  auth.globalData.ready = true;
  auth.syncAppGlobalData();
}

Page({
  data: {
    home: null,
    maskedPhone: '',
    displayName: '微信游客',
    loading: false,
    onboarding: true,
    savingProfile: false,
    authMode: 'UNKNOWN',
    needPhoneAuth: true,
    authFlowMode: 'login',
    profileForm: {
      nickname: '',
      phoneNumber: ''
    },
    phoneAuthHint: '',
    showAuthPopup: false
  },

  openAuthPopup() {
    this.setData({
      showAuthPopup: true,
      authFlowMode: 'login',
      phoneAuthHint: '',
      profileForm: {
        nickname: '',
        phoneNumber: ''
      }
    });
  },

  startRegisterFlow({ phoneNumber = '' } = {}) {
    this.setData({
      showAuthPopup: true,
      authFlowMode: 'register',
      phoneAuthHint: phoneNumber ? maskPhone(phoneNumber) : '',
      profileForm: {
        nickname: '',
        phoneNumber
      }
    });
  },

  startCompleteProfileFlow({ phoneNumber = '' } = {}) {
    this.setData({
      showAuthPopup: true,
      authFlowMode: 'complete-profile',
      phoneAuthHint: phoneNumber ? maskPhone(phoneNumber) : this.data.phoneAuthHint,
      'profileForm.nickname': ''
    });
  },

  closeAuthPopup() {
    this.setData({ showAuthPopup: false });
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 2
      })
    }
    this.refreshPage();
  },

  async refreshPage() {
    const app = getApp();
    await app.waitForAuth();
    const onboarding = !app.globalData.token;
    this.setData({
      onboarding,
      authMode: app.globalData.authMode,
      needPhoneAuth: !!app.globalData.needPhoneAuth
    });
    if (onboarding) {
      this.setData({
        home: null,
        maskedPhone: '',
        displayName: '微信游客'
      });
      wx.stopPullDownRefresh();
      return;
    }
    this.loadProfile();
  },

  async loadProfile() {
    const app = getApp();
    if (!app.globalData.token) {
      this.setData({ loading: false });
      return;
    }
    this.setData({ loading: true });
    try {
      const home = await request({ url: '/api/mobile/customer/home' });
      const finalName = displayName(home.name);
      this.setData({
        home,
        displayName: finalName,
        maskedPhone: maskPhone(home.phone)
      });
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  onPullDownRefresh() {
    this.refreshPage();
  },

  openAgreement() {
    wx.showModal({ title: '用户服务协议', content: '这里是用户服务协议的内容...', showCancel: false });
  },

  openPrivacy() {
    wx.showModal({ title: '隐私政策', content: '这里是隐私政策的内容...', showCancel: false });
  },

  onPhoneInput(e) {
    const phone = e.detail.value.replace(/\D/g, '');
    const hint = phone ? maskPhone(phone) : '';
    this.setData({
      'profileForm.phoneNumber': phone,
      phoneAuthHint: hint
    });
  },

  onNicknameInput(e) {
    this.setData({
      'profileForm.nickname': e.detail.value
    });
  },

  async preparePhonePrivacyPermission() {
    try {
      await ensurePhonePrivacyPermission();
    } catch (error) {
      wx.showToast({
        title: getPhonePrivacyErrorMessage(error),
        icon: 'none'
      });
    }
  },

  async getPhoneNumber(e) {
    // #region debug-point A:customer-phone-event
    wx.request({ url: 'http://192.168.1.3:7777/event', method: 'POST', data: { sessionId: 'wechat-phone-login', runId: 'pre-fix', hypothesisId: 'A', location: 'miniapp/pages/profile/index.js:getPhoneNumber:entry', msg: '[DEBUG] customer getPhoneNumber event', data: { errMsg: e && e.detail ? e.detail.errMsg : '', hasCode: !!(e && e.detail && e.detail.code), codeLength: e && e.detail && e.detail.code ? String(e.detail.code).length : 0 }, ts: Date.now() } });
    // #endregion
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      wx.showToast({
        title: getPhonePrivacyErrorMessage(e && e.detail),
        icon: 'none'
      });
      return;
    }

    if (this.data.savingProfile) return;
    this.setData({ savingProfile: true });

    try {
      const code = String(e.detail.code || '').trim();
      if (!code) {
        // #region debug-point A:customer-phone-missing-code
        wx.request({ url: 'http://192.168.1.3:7777/event', method: 'POST', data: { sessionId: 'wechat-phone-login', runId: 'pre-fix', hypothesisId: 'A', location: 'miniapp/pages/profile/index.js:getPhoneNumber:missing-code', msg: '[DEBUG] customer missing phone code', data: { errMsg: e.detail.errMsg }, ts: Date.now() } });
        // #endregion
        wx.showToast({ title: '微信手机号授权失败，请重试', icon: 'none' });
        return;
      }

      const result = await auth.bindPhone({ code });

      applyCustomerAuthResult(result);

      const home = await request({ url: '/api/mobile/customer/home' });
      if (isPlaceholderCustomerName(home && home.name)) {
        this.setData({
          onboarding: false,
          home
        });
        this.startCompleteProfileFlow({
          phoneNumber: home && home.phone ? String(home.phone).replace(/\D/g, '') : ''
        });
        wx.showToast({ title: '请填写姓名完成注册', icon: 'none' });
        return;
      }

      wx.showToast({ title: '登录成功', icon: 'success' });
      this.setData({ showAuthPopup: false, phoneAuthHint: '' });
      setTimeout(() => this.refreshPage(), 1200);
    } catch (error) {
      // #region debug-point B:customer-phone-error
      wx.request({ url: 'http://192.168.1.3:7777/event', method: 'POST', data: { sessionId: 'wechat-phone-login', runId: 'pre-fix', hypothesisId: 'B', location: 'miniapp/pages/profile/index.js:getPhoneNumber:catch', msg: '[DEBUG] customer bind phone failed', data: { message: error && error.message ? error.message : '' }, ts: Date.now() } });
      // #endregion
      if (shouldStartRegister(error)) {
        this.startRegisterFlow();
        wx.showToast({ title: '请填写姓名完成注册', icon: 'none' });
      } else {
        wx.showToast({ title: error.message || '微信授权失败', icon: 'none' });
      }
    } finally {
      this.setData({ savingProfile: false });
    }
  },

  async submitProfile() {
    if (this.data.authFlowMode === 'register') {
      return this.submitRegister();
    }
    if (this.data.authFlowMode === 'complete-profile') {
      return this.submitProfileCompletion();
    }
    return this.submitPhoneLogin();
  },

  async submitPhoneLogin() {
    const errorMessage = getSubmitProfileError({
      mode: 'login',
      nickname: '',
      phoneNumber: this.data.profileForm.phoneNumber
    });

    if (errorMessage) {
      wx.showToast({ title: errorMessage, icon: 'none' });
      return;
    }

    if (this.data.savingProfile) return;

    this.setData({ savingProfile: true });
    try {
      const result = await auth.phoneLogin(this.data.profileForm.phoneNumber);
      applyCustomerAuthResult(result);

      wx.showToast({ title: '登录成功', icon: 'success' });
      this.setData({
        onboarding: false,
        showAuthPopup: false,
        phoneAuthHint: ''
      });
      this.refreshPage();
    } catch (error) {
      if (shouldStartRegister(error)) {
        this.startRegisterFlow({
          phoneNumber: this.data.profileForm.phoneNumber
        });
        wx.showToast({ title: '请填写姓名完成注册', icon: 'none' });
      } else {
        wx.showToast({ title: error.message || '登录失败', icon: 'none' });
      }
    } finally {
      this.setData({ savingProfile: false });
    }
  },

  async submitRegister() {
    const errorMessage = getSubmitProfileError({
      mode: 'register',
      nickname: this.data.profileForm.nickname,
      phoneNumber: this.data.profileForm.phoneNumber
    });

    if (errorMessage) {
      wx.showToast({ title: errorMessage, icon: 'none' });
      return;
    }

    if (this.data.savingProfile) return;

    this.setData({ savingProfile: true });
    try {
      const result = await auth.register(
        this.data.profileForm.phoneNumber,
        this.data.profileForm.nickname.trim()
      );
      applyCustomerAuthResult(result);
      wx.showToast({ title: '注册成功', icon: 'success' });
      this.setData({
        onboarding: false,
        showAuthPopup: false,
        authFlowMode: 'login',
        phoneAuthHint: '',
        profileForm: {
          nickname: '',
          phoneNumber: ''
        }
      });
      this.refreshPage();
    } catch (error) {
      wx.showToast({ title: error.message || '注册失败', icon: 'none' });
    } finally {
      this.setData({ savingProfile: false });
    }
  },

  async submitProfileCompletion() {
    const nickname = String(this.data.profileForm.nickname || '').trim();
    if (!nickname) {
      wx.showToast({ title: '请输入姓名', icon: 'none' });
      return;
    }

    if (this.data.savingProfile) return;

    this.setData({ savingProfile: true });
    try {
      const result = await auth.completeProfile(nickname);
      applyCustomerAuthResult(result);

      wx.showToast({ title: '注册成功', icon: 'success' });
      this.setData({
        showAuthPopup: false,
        authFlowMode: 'login',
        profileForm: {
          nickname: '',
          phoneNumber: ''
        }
      });
      this.refreshPage();
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ savingProfile: false });
    }
  },

  handleLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: async (res) => {
        if (res.confirm) {
          await auth.logout();
          getApp().globalData.announcementShown = false;
          this.refreshPage();
          wx.showToast({ title: '已退出', icon: 'success' });
        }
      }
    });
  },

  goEditProfile() {
    if (this.data.onboarding) return;
    wx.showModal({
      title: '修改姓名',
      editable: true,
      placeholderText: '请输入您的姓名',
      content: this.data.home ? this.data.home.name : '',
      success: async (res) => {
        if (res.confirm && res.content) {
          try {
            await request({
              url: '/api/mobile/customer/profile',
              method: 'POST',
              data: { name: res.content }
            });
            wx.showToast({ title: '修改成功', icon: 'success' });
            this.loadProfile();
          } catch (error) {
            wx.showToast({ title: error.message || '修改失败', icon: 'none' });
          }
        }
      }
    });
  },

  guardMemberAction(targetUrl) {
    if (this.data.onboarding) {
      wx.showToast({ title: '先完成手机号验证，再查看会员服务', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: targetUrl });
  },

  goOrders() {
    this.guardMemberAction('/pages/orders/index');
  },

  goReceipts() {
    this.guardMemberAction('/pages/receipts/index');
  },

  goAddresses() {
    this.guardMemberAction('/pages/addresses/index');
  },

  goWallet() {
    this.guardMemberAction('/pages/wallet/index');
  },

  contactService() {
    if (this.data.onboarding) {
      wx.showToast({ title: '验证后可同步会员服务与订单支持', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '联系专属客服',
      content: '可在“联系客服”入口直接发起会话，或联系运营同事处理。',
      showCancel: false
    });
  }
});
