const { request } = require('../../utils/request');
const { maskPhone } = require('../../utils/mobile');
const { getSubmitProfileError } = require('../../utils/profile-auth');
const {
  ensurePhonePrivacyPermission,
  getPhonePrivacyErrorMessage,
  openPrivacyContract
} = require('../../utils/privacy-auth');
const auth = require('../../utils/auth');

const AGREEMENT_ACCEPTED_KEY = 'miniapp_customer_auth_agreement_accepted_v2';
function reportDebug() {
}

function isPlaceholderCustomerName(name) {
  const value = String(name || '').trim();
  return value.startsWith('微信用户-') || value.startsWith('待完善-');
}

function shouldStartRegister(error) {
  const message = String((error && error.message) || '').trim();
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

function clearAgreementAccepted() {
  try {
    wx.removeStorageSync(AGREEMENT_ACCEPTED_KEY);
  } catch (_) {}
}

Page({
  data: {
    savingProfile: false,
    wechatLoading: false,
    authFlowMode: 'login',
    profileForm: {
      nickname: '',
      phoneNumber: ''
    },
    phoneAuthHint: '',
    agreementAccepted: false,
    agreementSheetChecked: false,
    showAgreementSheet: false,
    pendingAgreementAction: ''
  },

  onLoad(options = {}) {
    const phoneNumber = String(options.phoneNumber || '').replace(/\D/g, '');
    const authFlowMode = options.mode === 'complete-profile' ? 'complete-profile' : 'login';
    this.setData({
      authFlowMode,
      agreementAccepted: false,
      agreementSheetChecked: false,
      phoneAuthHint: phoneNumber ? maskPhone(phoneNumber) : '',
      profileForm: {
        nickname: '',
        phoneNumber
      }
    });
  },

  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack();
      return;
    }
    wx.switchTab({ url: '/pages/profile/index' });
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
    const phone = String(e.detail.value || '').replace(/\D/g, '');
    this.setData({
      'profileForm.phoneNumber': phone,
      phoneAuthHint: phone ? maskPhone(phone) : ''
    });
  },

  onNicknameInput(e) {
    this.setData({
      'profileForm.nickname': e.detail.value
    });
  },

  goRegisterPage() {
    const phoneNumber = String(this.data.profileForm.phoneNumber || '').replace(/\D/g, '');
    const query = phoneNumber ? `?phoneNumber=${phoneNumber}` : '';
    wx.navigateTo({ url: `/pages/register/index${query}` });
  },

  handleAgreementBarTap() {
    this.openAgreementSheet('');
  },

  openAgreementSheet(action = '') {
    // #region debug-point A:customer-open-agreement
    reportDebug('miniapp/pages/login/index.js:openAgreementSheet', '[DEBUG] customer open agreement sheet', {
      action,
      agreementAccepted: this.data.agreementAccepted,
      agreementSheetChecked: this.data.agreementSheetChecked
    });
    // #endregion
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
    // #region debug-point A:customer-toggle-agreement
    reportDebug('miniapp/pages/login/index.js:toggleAgreementSheetChecked', '[DEBUG] customer toggle agreement checked', {
      nextChecked,
      agreementAcceptedBefore: this.data.agreementAccepted,
      agreementSheetCheckedBefore: this.data.agreementSheetChecked
    });
    // #endregion
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
    // #region debug-point A:customer-handle-agreement-primary
    reportDebug('miniapp/pages/login/index.js:handleAgreementPrimaryAction', '[DEBUG] customer click agreement primary', {
      pendingAgreementAction: this.data.pendingAgreementAction,
      agreementAccepted: this.data.agreementAccepted,
      agreementSheetChecked: this.data.agreementSheetChecked
    });
    // #endregion
    if (!this.ensureAgreementAccepted()) {
      return;
    }
    if (this.data.pendingAgreementAction === 'submitProfile') {
      this.submitProfile();
      return;
    }
    if (this.data.pendingAgreementAction === 'wechat-login') {
      wx.showToast({
        title: '已勾选协议，请再次点击微信登录',
        icon: 'none'
      });
    }
  },

  openAgreementSheetForWechat() {
    // #region debug-point A:customer-wechat-open-agreement
    reportDebug('miniapp/pages/login/index.js:openAgreementSheetForWechat', '[DEBUG] customer click wechat login before agreement', {
      agreementAccepted: this.data.agreementAccepted,
      agreementSheetChecked: this.data.agreementSheetChecked
    });
    // #endregion
    this.openAgreementSheet('wechat-login');
  },

  async prepareWechatLogin() {
    // #region debug-point B:customer-prepare-wechat
    reportDebug('miniapp/pages/login/index.js:prepareWechatLogin:entry', '[DEBUG] customer prepare wechat login entry', {
      agreementAccepted: this.data.agreementAccepted,
      wechatLoading: this.data.wechatLoading,
      savingProfile: this.data.savingProfile
    });
    // #endregion
    try {
      await ensurePhonePrivacyPermission();
      // #region debug-point B:customer-prepare-wechat-success
      reportDebug('miniapp/pages/login/index.js:prepareWechatLogin:success', '[DEBUG] customer prepare wechat login success', {});
      // #endregion
      return true;
    } catch (error) {
      // #region debug-point B:customer-prepare-wechat-fail
      reportDebug('miniapp/pages/login/index.js:prepareWechatLogin:fail', '[DEBUG] customer prepare wechat login fail', {
        errMsg: error && error.errMsg ? error.errMsg : '',
        message: error && error.message ? error.message : ''
      });
      // #endregion
      wx.showToast({
        title: getPhonePrivacyErrorMessage(error),
        icon: 'none'
      });
      return false;
    }
  },

  async handleWechatPermissionFailure(detail) {
    const directMessage = getPhonePrivacyErrorMessage(detail);
    if (detail && detail.errMsg) {
      wx.showToast({
        title: directMessage,
        icon: 'none'
      });
      return;
    }
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

  handleSubmitEntry() {
    if (this.data.agreementAccepted) {
      this.submitProfile();
      return;
    }
    this.openAgreementSheet('submitProfile');
  },

  isWechatBusy() {
    return !!(this.data.savingProfile || this.data.wechatLoading);
  },

  async startWechatLoginFlow(detail) {
    // #region debug-point C:customer-start-wechat-flow
    reportDebug('miniapp/pages/login/index.js:startWechatLoginFlow', '[DEBUG] customer start wechat login flow', {
      hasDetail: !!detail,
      errMsg: detail && detail.errMsg ? detail.errMsg : '',
      hasCode: !!(detail && detail.code),
      codeLength: detail && detail.code ? String(detail.code).length : 0,
      isWechatBusy: this.isWechatBusy()
    });
    // #endregion
    if (this.isWechatBusy()) {
      return;
    }
    if (!detail || detail.errMsg !== 'getPhoneNumber:ok') {
      await this.handleWechatPermissionFailure(detail);
      return;
    }
    return this.doLoginWithCode(detail.code);
  },

  async getPhoneNumber(e) {
    // #region debug-point C:customer-get-phone
    reportDebug('miniapp/pages/login/index.js:getPhoneNumber', '[DEBUG] customer getPhoneNumber callback', {
      hasDetail: !!(e && e.detail),
      errMsg: e && e.detail && e.detail.errMsg ? e.detail.errMsg : '',
      hasCode: !!(e && e.detail && e.detail.code),
      codeLength: e && e.detail && e.detail.code ? String(e.detail.code).length : 0
    });
    // #endregion
    return this.startWechatLoginFlow(e && e.detail);
  },

  async doLoginWithCode(code) {
    // #region debug-point D:customer-do-login
    reportDebug('miniapp/pages/login/index.js:doLoginWithCode', '[DEBUG] customer do login with code', {
      hasCode: !!code,
      codeLength: code ? String(code).length : 0,
      isWechatBusy: this.isWechatBusy()
    });
    // #endregion
    if (this.isWechatBusy()) return;
    this.setData({ wechatLoading: true });
    try {
      const finalCode = String(code || '').trim();
      if (!finalCode) {
        wx.showToast({ title: '微信手机号授权失败，请重试', icon: 'none' });
        return;
      }

      const result = await auth.bindPhone({ code: finalCode });
      applyCustomerAuthResult(result);

      const home = await request({ url: '/api/mobile/customer/home' });
      if (isPlaceholderCustomerName(home && home.name)) {
        this.setData({
          authFlowMode: 'complete-profile',
          phoneAuthHint: home && home.phone ? maskPhone(home.phone) : this.data.phoneAuthHint,
          'profileForm.nickname': '',
          'profileForm.phoneNumber': home && home.phone ? String(home.phone).replace(/\D/g, '') : this.data.profileForm.phoneNumber
        });
        wx.showToast({ title: '请填写姓名完成注册', icon: 'none' });
        return;
      }

      wx.showToast({ title: '登录成功', icon: 'success' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/profile/index' });
      }, 800);
    } catch (error) {
      if (shouldStartRegister(error)) {
        this.goRegisterPage();
        wx.showToast({ title: '请填写姓名完成注册', icon: 'none' });
      } else {
        wx.showToast({ title: error.message || '微信授权失败', icon: 'none' });
      }
    } finally {
      this.setData({ wechatLoading: false });
    }
  },

  async submitProfile() {
    if (!this.data.agreementAccepted) {
      wx.showToast({ title: '请先阅读并同意协议', icon: 'none' });
      return;
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
      setTimeout(() => {
        wx.switchTab({ url: '/pages/profile/index' });
      }, 800);
    } catch (error) {
      if (shouldStartRegister(error)) {
        this.goRegisterPage();
        wx.showToast({ title: '请填写姓名完成注册', icon: 'none' });
      } else {
        wx.showToast({ title: error.message || '登录失败', icon: 'none' });
      }
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
      setTimeout(() => {
        wx.switchTab({ url: '/pages/profile/index' });
      }, 800);
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ savingProfile: false });
    }
  }
});
