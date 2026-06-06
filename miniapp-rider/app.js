const auth = require('./utils/auth');
const { DEFAULT_API_BASE_URL, resolveApiBaseUrl } = require('./utils/api-base');

const UNOPENED_RIDER_ACCOUNT_MESSAGE = '后台未开通该手机号对应的骑手账号';

App({
  globalData: {
    apiBaseUrl: DEFAULT_API_BASE_URL,
    statusBarHeight: 0,
    navBarHeight: 0, // 导航栏总高度（状态栏 + 标题栏）
    menuButtonTop: 0, // 胶囊按钮距离顶部的距离
    menuButtonHeight: 0, // 胶囊按钮高度
    cloudEnvId: 'cloud1-4g88w3e2dedee471', // 云开发环境ID
    riderAuthReady: false,
    riderRegistered: false,
    riderOpenid: '',
    riderStatus: 'UNAUTHORIZED',
    riderName: '',
    riderProfile: null
  },

  onLaunch() {
    const sysInfo = wx.getSystemInfoSync();
    const menuButton = wx.getMenuButtonBoundingClientRect();
    
    // 状态栏高度
    this.globalData.statusBarHeight = sysInfo.statusBarHeight || 20;
    
    // 胶囊按钮信息
    this.globalData.menuButtonTop = menuButton.top;
    this.globalData.menuButtonHeight = menuButton.height;
    
    // 导航栏总高度 = 胶囊按钮底部位置 + 胶囊按钮下方的间距
    // 胶囊按钮下方间距 = 胶囊按钮距离顶部 - 状态栏高度（这个间距在上下都有）
    const gap = menuButton.top - this.globalData.statusBarHeight;
    this.globalData.navBarHeight = menuButton.top + menuButton.height + gap;

    this.globalData.apiBaseUrl = resolveApiBaseUrl(wx.getStorageSync('apiBaseUrl'));

    // 初始化云开发
    if (wx.cloud && this.globalData.cloudEnvId) {
      wx.cloud.init({
        env: this.globalData.cloudEnvId,
        traceUser: true
      });
      console.log('[云开发] 初始化成功', this.globalData.cloudEnvId);
    }

    // 初始化统一认证
    this.authPromise = auth.init()
      .catch((error) => {
        console.error('[自动登录] 失败', error);
        return null;
      });

    // 根据认证状态自动跳转
    this.authPromise.then(() => {
      this.syncRiderGlobals();
      const entryPage = auth.getRiderEntryPage();
      if (entryPage) {
        setTimeout(() => {
          wx.reLaunch({ url: entryPage });
        }, 100);
      }
    });
  },

  waitForRiderAuth() {
    return auth.waitForAuth().then(() => {
      this.syncRiderGlobals();
    });
  },

  async loginWithPhone(phone) {
    const result = await auth.phoneLogin(phone);
    this.syncRiderGlobals();
    return result;
  },

  async verifyToken(token) {
    try {
      await auth.verifyToken(token);
      return true;
    } catch (error) {
      return false;
    }
  },

  getRiderViewState() {
    const state = auth.getAuthState();
    if (!state.ready) return 'loading';
    if (!state.loggedIn) return 'guest';
    if (!state.registered) return 'guest';
    if (state.riderStatus === 'ACTIVE') return 'active';
    if (state.riderStatus === 'PENDING') return 'pending';
    if (state.riderStatus === 'DISABLED') return 'blocked';
    if (state.riderStatus === 'NOT_FOUND') return 'not_found';
    return 'unknown';
  },

  canUseWorkbench() {
    return auth.canUseWorkbench();
  },

  resolveRiderEntryPage() {
    return auth.getRiderEntryPage();
  },

  async ensureWorkbenchAccess() {
    return auth.ensureWorkbenchAccess();
  },

  getWorkbenchBlockMessage() {
    return auth.getWorkbenchBlockMessage() || UNOPENED_RIDER_ACCOUNT_MESSAGE;
  },

  openWorkbench() {
    wx.reLaunch({ url: '/pages/today/index' });
  },

  getActiveRiderName() {
    return auth.getAuthState().riderName;
  },

  async refreshRiderProfile() {
    await auth.loadRiderProfile();
    this.syncRiderGlobals();
    return auth.getAuthState();
  },

  async logoutRider() {
    await auth.logout();
    this.syncRiderGlobals();
    wx.switchTab({ url: '/pages/profile/index' });
  },

  async resetRiderAuthState() {
    await auth.logout();
    this.syncRiderGlobals();
  },

  syncRiderGlobals() {
    const state = auth.getAuthState();
    this.globalData.riderAuthReady = state.ready;
    this.globalData.riderRegistered = state.registered;
    this.globalData.riderOpenid = auth.globalData.openid || '';
    this.globalData.riderStatus = state.riderStatus;
    this.globalData.riderName = state.riderName;
    this.globalData.riderProfile = {
      ...(this.globalData.riderProfile || {}),
      riderName: state.riderName,
      phone: state.phone,
      riderStatus: state.riderStatus
    };
  },

  getJson(path) {
    return auth.request(path, 'GET');
  },

  postJson(path, data) {
    return auth.request(path, 'POST', data);
  }
});
