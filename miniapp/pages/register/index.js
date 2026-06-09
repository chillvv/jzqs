const auth = require('../../utils/auth');
const { maskPhone } = require('../../utils/mobile');
const { getSubmitProfileError } = require('../../utils/profile-auth');

function returnToLoginPage(phoneNumber) {
  const pages = getCurrentPages();
  const previousPage = pages[pages.length - 2];

  if (previousPage && previousPage.route === 'pages/login/index' && typeof previousPage.setData === 'function') {
    previousPage.setData({
      phoneAuthHint: phoneNumber ? maskPhone(phoneNumber) : '',
      'profileForm.phoneNumber': phoneNumber || ''
    });
    wx.navigateBack();
    return;
  }

  const query = phoneNumber ? `?phoneNumber=${phoneNumber}` : '';
  wx.redirectTo({ url: `/pages/login/index${query}` });
}

Page({
  data: {
    form: {
      nickname: '',
      phoneNumber: ''
    },
    phoneHint: '',
    saving: false
  },

  onNicknameInput(e) {
    this.setData({
      'form.nickname': e.detail.value
    });
  },

  onPhoneInput(e) {
    const phoneNumber = String(e.detail.value || '').replace(/\D/g, '');
    this.setData({
      'form.phoneNumber': phoneNumber,
      phoneHint: phoneNumber ? maskPhone(phoneNumber) : ''
    });
  },

  async submitRegister() {
    const errorMessage = getSubmitProfileError({
      mode: 'register',
      nickname: this.data.form.nickname,
      phoneNumber: this.data.form.phoneNumber
    });
    if (errorMessage) {
      wx.showToast({ title: errorMessage, icon: 'none' });
      return;
    }

    if (this.data.saving) return;

    this.setData({ saving: true });
    try {
      await auth.register(this.data.form.phoneNumber, this.data.form.nickname.trim());

      const phoneNumber = this.data.form.phoneNumber;
      wx.showToast({ title: '注册成功，请登录', icon: 'success' });
      setTimeout(() => {
        returnToLoginPage(phoneNumber);
      }, 1200);
    } catch (error) {
      wx.showToast({ title: error.message || '注册失败', icon: 'none' });
    } finally {
      this.setData({ saving: false });
    }
  },

  backToLogin() {
    returnToLoginPage(this.data.form.phoneNumber);
  }
});
