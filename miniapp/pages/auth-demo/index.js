const auth = require('../../utils/auth');
const { ensurePhonePrivacyPermission, getPhonePrivacyErrorMessage } = require('../../utils/privacy-auth');

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
    if (!authState.ready) return '璁よ瘉鍒濆鍖栦腑...';
    if (!authState.loggedIn) return '鏈櫥褰?;
    if (!authState.registered) return '宸茬櫥褰曪紝鏈粦瀹氭墜鏈哄彿';
    return `宸茬櫥褰曪紝鐢ㄦ埛ID: ${authState.userId}`;
  },

  async handleSilentLogin() {
    this.setData({ loading: true, message: '闈欓粯鐧诲綍涓?..' });
    try {
      await auth.silentLogin();
      this.loadAuthState();
      wx.showToast({ title: '闈欓粯鐧诲綍鎴愬姛', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  handleWechatLogin() {
    wx.showModal({
      title: '寰俊涓€閿櫥褰?,
      content: '璇风偣鍑讳笅鏂规寜閽繘琛屽井淇′竴閿櫥褰?,
      showCancel: false,
      confirmText: '鐭ラ亾浜?
    });
  },

  async preparePhonePrivacyPermission() {
    try {
      await ensurePhonePrivacyPermission();
    } catch (error) {
      wx.showToast({ title: getPhonePrivacyErrorMessage(error), icon: 'none' });
    }
  },

  handleLogout() {
    auth.logout();
    this.loadAuthState();
    wx.showToast({ title: '宸查€€鍑虹櫥褰?, icon: 'success' });
  },

  // 寰俊涓€閿櫥褰曟寜閽簨浠?
  handleGetPhoneNumber(e) {
    if (e.detail.errMsg === 'getPhoneNumber:ok') {
      const code = String(e.detail.code || '').trim();
      if (!code) {
        wx.showToast({ title: '寰俊鎺堟潈澶辫触锛岃閲嶈瘯', icon: 'none' });
        return;
      }
      this.bindPhone(code);
    } else {
      wx.showToast({ title: getPhonePrivacyErrorMessage(e && e.detail), icon: 'none' });
    }
  },

  async bindPhone(code) {
    this.setData({ loading: true, message: '缁戝畾鎵嬫満鍙蜂腑...' });
    try {
      await auth.bindPhone({ code });
      this.loadAuthState();
      wx.showToast({ title: '缁戝畾鎴愬姛', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  async testApi() {
    try {
      const token = auth.globalData.token || wx.getStorageSync('auth_token');
      const result = await auth.request('/api/mobile/auth/verify', 'GET', null, token);
      wx.showToast({ title: 'API 娴嬭瘯鎴愬姛', icon: 'success' });
      console.log('API 娴嬭瘯缁撴灉:', result);
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' });
    }
  }
});
