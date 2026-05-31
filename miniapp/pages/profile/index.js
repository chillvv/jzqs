const { request } = require('../../utils/request');
const { maskPhone } = require('../../utils/mobile');
const { getSubmitProfileError } = require('../../utils/profile-auth');
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
  if (!result || !result.token) {
    return;
  }
  wx.setStorageSync('auth_token', result.token);
  auth.globalData.token = result.token;
  auth.globalData.userId = result.userId;
  auth.globalData.userType = result.userType || 'customer';
  auth.globalData.loggedIn = true;
  auth.globalData.registered = true;
  auth.globalData.needPhoneAuth = false;
  auth.globalData.ready = true;
  getApp().globalData.token = result.token;
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

  async getPhoneNumber(e) {
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      wx.showToast({ title: '获取手机号失败', icon: 'none' });
      return;
    }

    if (this.data.savingProfile) return;
    this.setData({ savingProfile: true });

    try {
      const app = getApp();
      const code = e.detail.code;

      // 新版 API：code 直接换手机号并登录/注册
      const result = await new Promise((resolve, reject) => {
        wx.request({
          url: app.globalData.apiBaseUrl + '/api/auth/bind-phone',
          method: 'POST',
          header: { 'content-type': 'application/json' },
          data: { code, userType: 'customer' },
          success(res) {
            const body = res.data || {};
            if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
              resolve(body.data);
            } else {
              reject(new Error(body.message || '微信授权失败'));
            }
          },
          fail() { reject(new Error('无法连接服务器')); }
        });
      });

      applyCustomerAuthResult(result);

      const home = await request({ url: '/api/mobile/customer/home' });
      if (isPlaceholderCustomerName(home && home.name)) {
        this.setData({
          onboarding: false,
          home
        });
        this.startCompleteProfileFlow({
          phoneNumber: e.detail.phoneNumber || ''
        });
        wx.showToast({ title: '请填写姓名完成注册', icon: 'none' });
        return;
      }

      wx.showToast({ title: '登录成功', icon: 'success' });
      this.setData({ showAuthPopup: false, phoneAuthHint: '' });
      setTimeout(() => this.refreshPage(), 1200);
    } catch (error) {
      // 降级：把手机号填入表单（开发模式 / 旧版基础库）
      const phone = e.detail.phoneNumber || '';
      if (shouldStartRegister(error)) {
        this.startRegisterFlow({ phoneNumber: phone });
        wx.showToast({ title: '请填写姓名完成注册', icon: 'none' });
      } else if (phone) {
        this.setData({
          'profileForm.phoneNumber': phone,
          phoneAuthHint: maskPhone(phone)
        });
        wx.showToast({ title: '手机号已填入，请点确认登录', icon: 'none' });
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
      const app = getApp();
      const apiBaseUrl = app.globalData.apiBaseUrl;
      const authState = auth.getAuthState();

      const result = await new Promise((resolve, reject) => {
        wx.request({
          url: apiBaseUrl + '/api/auth/customer-phone-login',
          method: 'POST',
          data: {
            phone: this.data.profileForm.phoneNumber,
            openid: authState.openid || ''
          },
          success(res) {
            const body = res.data || {};
            if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
              resolve(body.data);
            } else {
              reject(new Error(body.message || '登录失败'));
            }
          },
          fail() {
            reject(new Error('暂时无法连接服务'));
          }
        });
      });

      if (result.token) {
        wx.setStorageSync('auth_token', result.token);
        auth.globalData.token = result.token;
        auth.globalData.userId = result.userId;
        auth.globalData.userType = result.userType || 'customer';
        auth.globalData.loggedIn = true;
        auth.globalData.registered = true;
        auth.globalData.needPhoneAuth = false;
        auth.globalData.ready = true;
        getApp().globalData.token = result.token;
      }

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
      const app = getApp();
      const authState = auth.getAuthState();

      const result = await new Promise((resolve, reject) => {
        wx.request({
          url: app.globalData.apiBaseUrl + '/api/auth/register-phone',
          method: 'POST',
          data: {
            phone: this.data.profileForm.phoneNumber,
            nickname: this.data.profileForm.nickname.trim(),
            openid: authState.openid || '',
            userType: 'customer'
          },
          success(res) {
            const body = res.data || {};
            if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
              resolve(body.data);
            } else {
              reject(new Error(body.message || '注册失败'));
            }
          },
          fail() {
            reject(new Error('暂时无法连接服务'));
          }
        });
      });

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
      await request({
        url: '/api/mobile/customer/profile',
        method: 'POST',
        data: { name: nickname }
      });

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
      success: (res) => {
        if (res.confirm) {
          wx.removeStorageSync('auth_token');
          getApp().globalData.token = null;
          getApp().globalData.loggedIn = false;
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
