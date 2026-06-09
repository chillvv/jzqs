/**
 * 骑手注册页面
 */

const auth = require('../../utils/auth');

Page({
  data: {
    phone: '',
    name: '',
    agreed: false,
    loading: false
  },

  /**
   * 页面加载时读取上次登录的手机号
   */
  onLoad(options = {}) {
    try {
      const initialPhone = String(options.phoneNumber || wx.getStorageSync('lastLoginPhone') || '');
      if (initialPhone) {
        this.setData({ phone: initialPhone });
      }
    } catch (error) {
      console.error('[读取上次手机号失败]', error);
    }
  },

  onNameInput(e) {
    this.setData({ name: e.detail.value });
  },

  onPhoneInput(e) {
    this.setData({ phone: e.detail.value });
  },

  onAgreeChange(e) {
    this.setData({ agreed: e.detail.value.length > 0 });
  },

  async onRegister() {
    const { phone, name, agreed } = this.data;

    // 验证表单
    if (!name || name.trim() === '') {
      wx.showToast({ title: '请输入姓名', icon: 'none' });
      return;
    }

    if (!phone || !/^1\d{10}$/.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' });
      return;
    }

    if (!agreed) {
      wx.showToast({ title: '请阅读并同意协议', icon: 'none' });
      return;
    }

    this.setData({ loading: true });

    try {
      const app = getApp();
      await app.waitForRiderAuth();
      const openid = auth.globalData.openid || app.globalData.riderOpenid;

      const response = await auth.register(phone, name, openid);

      if (response.token) {
        // 注册成功后保存手机号
        try {
          wx.setStorageSync('lastLoginPhone', phone);
        } catch (error) {
          console.error('[保存手机号失败]', error);
        }
        app.syncRiderGlobals();

        wx.showToast({
          title: response.message || '注册成功',
          icon: 'success',
          duration: 2000
        });

        setTimeout(() => {
          wx.switchTab({ url: '/pages/profile/index' });
        }, 2000);
      } else {
        wx.showToast({ title: response.message || '注册失败', icon: 'none' });
      }
    } catch (error) {
      console.error('[注册] 失败', error);
      wx.showToast({ title: error.message || '注册失败，请重试', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  goToLogin() {
    const phone = String(this.data.phone || '').trim();
    const query = phone ? `?phoneNumber=${phone}` : '';
    wx.redirectTo({ url: `/pages/login/index${query}` });
  },

  onViewAgreement() {
    wx.showModal({
      title: '用户协议',
      content: '这里是用户协议内容...',
      showCancel: false
    });
  }
});
