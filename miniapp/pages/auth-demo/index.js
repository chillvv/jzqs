const auth = require('../../utils/auth');

Page({
  data: {
    authState: null,
    loading: false,
    message: ''
  },

  onLoad() {
    this.loadAuthState();
  },

  onShow() {
    this.loadAuthState();
  },

  loadAuthState() {
    const authState = auth.getAuthState();
    this.setData({
      authState,
      message: this.getAuthMessage(authState)
    });
  },

  getAuthMessage(authState) {
    if (!authState.ready) return '认证初始化中...';
    if (!authState.loggedIn) return '未登录';
    if (!authState.registered) return '已登录，未绑定手机号';
    return `已登录，用户ID: ${authState.userId}`;
  },

  async handleSilentLogin() {
    this.setData({ loading: true, message: '静默登录中...' });
    try {
      await auth.silentLogin();
      this.loadAuthState();
      wx.showToast({ title: '静默登录成功', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  handleWechatLogin() {
    wx.showModal({
      title: '微信一键登录',
      content: '请点击下方按钮进行微信一键登录',
      showCancel: false,
      confirmText: '知道了'
    });
  },

  handleLogout() {
    auth.logout();
    this.loadAuthState();
    wx.showToast({ title: '已退出登录', icon: 'success' });
  },

  // 微信一键登录按钮事件
  handleGetPhoneNumber(e) {
    if (e.detail.errMsg === 'getPhoneNumber:ok') {
      this.bindPhone(e.detail.code);
    } else {
      wx.showToast({ title: '取消授权', icon: 'none' });
    }
  },

  async bindPhone(code) {
    this.setData({ loading: true, message: '绑定手机号中...' });
    try {
      await auth.bindPhone(code);
      this.loadAuthState();
      wx.showToast({ title: '绑定成功', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  async testApi() {
    try {
      const result = await auth.request('/api/auth/verify', 'GET');
      wx.showToast({ title: 'API 测试成功', icon: 'success' });
      console.log('API 测试结果:', result);
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' });
    }
  }
});
