const { request } = require('../../utils/request');
const { maskPhone } = require('../../utils/mobile');
const { getSubmitProfileError } = require('../../utils/profile-auth');
const {
  ensurePhonePrivacyPermission,
  getPhonePrivacyErrorMessage,
  openPrivacyContract
} = require('../../utils/privacy-auth');
const auth = require('../../utils/auth');

const AGREEMENT_ACCEPTED_KEY = 'miniapp_customer_auth_agreement_accepted_v1';
const DELIVERY_TEMPLATE_ID = 'DCpNx6852oVCXO83CKuR-uO8WsgvVEDdAaUgwkLNi3s';
const ACCEPTED_DELIVERY_SUBSCRIPTION_RESULTS = ['accept', 'acceptWithAudio', 'acceptWithAlert'];

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

async function requestProfileSubscribeMessageTest() {
  if (typeof wx.requestSubscribeMessage !== 'function') {
    throw new Error('当前微信版本不支持订阅消息测试');
  }
  const subscribeResult = await new Promise((resolve) => {
    wx.requestSubscribeMessage({
      tmplIds: [DELIVERY_TEMPLATE_ID],
      success: resolve,
      fail() {
        resolve({});
      }
    });
  });
  const acceptResult = typeof subscribeResult[DELIVERY_TEMPLATE_ID] === 'string'
    ? subscribeResult[DELIVERY_TEMPLATE_ID]
    : '';
  return ACCEPTED_DELIVERY_SUBSCRIPTION_RESULTS.includes(acceptResult) ? acceptResult : '';
}

async function sendProfileSubscribeMessageTest(acceptResult) {
  return request({
    url: '/api/mobile/customer/subscribe-message/test-send',
    method: 'POST',
    header: { 'content-type': 'application/json' },
    data: {
      templateId: DELIVERY_TEMPLATE_ID,
      acceptResult
    }
  });
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
    showAuthPopup: false,
    agreementAccepted: false,
    agreementSheetChecked: false,
    showAgreementSheet: false,
    pendingAgreementAction: '',
    sendingSubscribeMessageTest: false,
    statusBarHeight: 0,
    navBarHeight: 44
  },

  onLoad() {
    const app = getApp();
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight
    });
  },

  goLoginPage() {
    wx.navigateTo({ url: '/pages/login/index' });
  },

  openAuthPopup() {
    this.goLoginPage();
  },

  startRegisterFlow({ phoneNumber = '' } = {}) {
    this.setData({
      showAuthPopup: true,
      authFlowMode: 'register',
      phoneAuthHint: phoneNumber ? maskPhone(phoneNumber) : '',
      agreementAccepted: false,
      agreementSheetChecked: false,
      showAgreementSheet: false,
      pendingAgreementAction: '',
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
      agreementAccepted: false,
      agreementSheetChecked: false,
      showAgreementSheet: false,
      pendingAgreementAction: '',
      'profileForm.nickname': '',
      'profileForm.phoneNumber': String(phoneNumber || '').replace(/\D/g, '')
    });
  },

  closeAuthPopup() {
    this.setData({
      showAuthPopup: false,
      showAgreementSheet: false,
      pendingAgreementAction: ''
    });
  },

  stopPropagation() {},

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
    wx.showModal({
      title: '用户服务协议',
      content: '为完成登录、下单与配送通知服务，我们会在你使用过程中处理账号标识、手机号、订单与收货信息。继续使用前，请先阅读并同意用户服务协议。',
      showCancel: false
    });
  },

  openPrivacy() {
    openPrivacyContract().catch((error) => {
      wx.showToast({
        title: getPhonePrivacyErrorMessage(error),
        icon: 'none'
      });
    });
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

  toggleAgreementSheetChecked() {
    this.setData({
      agreementSheetChecked: !this.data.agreementSheetChecked
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

  openAgreementSheetForWechat() {
    this.openAgreementSheet('wechat-login');
  },

  async prepareWechatLogin() {
    try {
      await ensurePhonePrivacyPermission();
      return true;
    } catch (error) {
      wx.showToast({
        title: getPhonePrivacyErrorMessage(error),
        icon: 'none'
      });
      return false;
    }
  },

  async handleWechatPermissionFailure(detail) {
    try {
      await ensurePhonePrivacyPermission();
      wx.showToast({
        title: '已完成微信隐私授权，请再次点击微信一键登录',
        icon: 'none'
      });
      return;
    } catch (_) {
      wx.showToast({
        title: getPhonePrivacyErrorMessage(detail),
        icon: 'none'
      });
    }
  },

  async completeAgreementAndContinueWechat(e) {
    if (!this.ensureAgreementAccepted()) {
      return;
    }
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      await this.handleWechatPermissionFailure(e && e.detail);
      return;
    }
    this.doLoginWithCode(e.detail.code);
  },

  handleSubmitEntry() {
    if (this.data.agreementAccepted) {
      this.submitProfile();
      return;
    }
    this.openAgreementSheet('submitProfile');
  },

  async getPhoneNumber(e) {
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      await this.handleWechatPermissionFailure(e && e.detail);
      return;
    }
    this.doLoginWithCode(e.detail.code);
  },

  async doLoginWithCode(code) {
    if (this.data.savingProfile) return;
    this.setData({ savingProfile: true });

    try {
      code = String(code || '').trim();
      if (!code) {
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
    if (!this.data.agreementAccepted) {
      wx.showToast({ title: '请先阅读并同意协议', icon: 'none' });
      return;
    }
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
    const phoneNumber = this.data.profileForm.phoneNumber || (this.data.home && this.data.home.phone) || '';
    const errorMessage = getSubmitProfileError({
      mode: 'register',
      nickname,
      phoneNumber
    });

    if (errorMessage) {
      wx.showToast({ title: errorMessage, icon: 'none' });
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
  async guardMemberAction(targetUrl) {
    const app = getApp();
    await app.waitForAuth();
    if (!app.globalData.token) {
      wx.showToast({ title: '先完成手机号验证，再查看会员服务', icon: 'none' });
      this.goLoginPage();
      return;
    }
    if (this.data.onboarding) {
      this.setData({
        onboarding: false,
        authMode: app.globalData.authMode,
        needPhoneAuth: !!app.globalData.needPhoneAuth
      });
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

  async testSubscribeMessage() {
    const app = getApp();
    await app.waitForAuth();
    if (!app.globalData.token) {
      wx.showToast({ title: '请先登录后测试', icon: 'none' });
      this.goLoginPage();
      return;
    }
    if (this.data.sendingSubscribeMessageTest) {
      return;
    }
    this.setData({ sendingSubscribeMessageTest: true });
    try {
      const acceptResult = await requestProfileSubscribeMessageTest();
      if (!acceptResult) {
        wx.showToast({ title: '你还没有同意订阅消息授权', icon: 'none' });
        return;
      }
      await sendProfileSubscribeMessageTest(acceptResult);
      wx.showToast({ title: '测试消息已发送', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message || '测试发送失败', icon: 'none' });
    } finally {
      this.setData({ sendingSubscribeMessageTest: false });
    }
  },

  async contactService() {
    const app = getApp();
    await app.waitForAuth();
    if (!app.globalData.token) {
      wx.showToast({ title: '验证后可同步会员服务与订单支持', icon: 'none' });
      this.goLoginPage();
      return;
    }
    wx.showModal({
      title: '联系专属客服',
      content: '可在“联系客服”入口直接发起会话，或联系运营同事处理。',
      showCancel: false
    });
  }
});
