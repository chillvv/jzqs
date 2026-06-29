const auth = require('./utils/auth');
const {
  DEFAULT_API_BASE_URL,
  DEFAULT_CLOUD_ENV_ID,
  DEFAULT_SERVICE_HEADERS,
  resolveApiBaseUrl,
  resolveCloudEnvId,
  resolveServiceHeaders
} = require('./utils/api-base');
const realtime = require('./utils/realtime');

const UNOPENED_RIDER_ACCOUNT_MESSAGE = '后台未开通该手机号对应的骑手账号';

App({
  globalData: {
    apiBaseUrl: DEFAULT_API_BASE_URL,
    statusBarHeight: 0,
    navBarHeight: 0, // 导航栏总高度（状态栏 + 标题栏）
    menuButtonTop: 0, // 胶囊按钮距离顶部的距离
    menuButtonHeight: 0, // 胶囊按钮高度
    cloudEnvId: DEFAULT_CLOUD_ENV_ID, // 云开发环境ID
    serviceHeaders: { ...DEFAULT_SERVICE_HEADERS },
    riderAuthReady: false,
    riderRegistered: false,
    riderOpenid: '',
    riderStatus: 'UNAUTHORIZED',
    riderName: '',
    riderProfile: null,
    riderWorkbenchDate: '',
    authRedirecting: false
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
    this.globalData.cloudEnvId = resolveCloudEnvId(wx.getStorageSync('cloudEnvId'));
    this.globalData.serviceHeaders = resolveServiceHeaders(wx.getStorageSync('serviceHeaders'));

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
    realtime.init({
      clientLabel: 'rider',
      getToken: () => wx.getStorageSync('auth_token') || auth.globalData.token || ''
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
    wx.reLaunch({ url: '/pages/queue/index' });
  },

  getActiveRiderName() {
    return auth.getAuthState().riderName;
  },

  getWorkbenchDate() {
    return this.globalData.riderWorkbenchDate || '';
  },

  setWorkbenchDate(date) {
    this.globalData.riderWorkbenchDate = String(date || '').trim();
  },

  resetWorkbenchDate() {
    this.globalData.riderWorkbenchDate = '';
  },

  async refreshRiderProfile() {
    await auth.loadRiderProfile();
    this.syncRiderGlobals();
    return auth.getAuthState();
  },

  async logoutRider() {
    realtime.stop();
    await auth.logout();
    this.syncRiderGlobals();
    wx.switchTab({ url: '/pages/profile/index' });
  },

  async resetRiderAuthState(options = {}) {
    const shouldRedirect = options.redirect === true;
    const message = typeof options.message === 'string' ? options.message.trim() : '';
    realtime.stop();
    await auth.logout();
    this.syncRiderGlobals();
    if (!shouldRedirect || this.globalData.authRedirecting) {
      return;
    }
    this.globalData.authRedirecting = true;
    if (message) {
      wx.showToast({
        title: message,
        icon: 'none',
        duration: 2000
      });
    }
    wx.switchTab({ url: '/pages/profile/index' });
    setTimeout(() => {
      this.globalData.authRedirecting = false;
    }, 1500);
  },

  syncRiderGlobals() {
    const state = auth.getAuthState();
    this.globalData.riderAuthReady = state.ready;
    this.globalData.riderRegistered = state.registered;
    this.globalData.riderOpenid = auth.globalData.openid || '';
    this.globalData.riderStatus = state.riderStatus;
    this.globalData.riderName = state.riderName;
    
    if (!state.loggedIn) {
      this.globalData.riderProfile = null;
    } else {
      this.globalData.riderProfile = {
        ...(this.globalData.riderProfile || {}),
        riderName: state.riderName,
        phone: state.phone,
        riderStatus: state.riderStatus
      };
    }
  },

  getJson(path) {
    return auth.request(path, 'GET');
  },

  postJson(path, data) {
    return auth.request(path, 'POST', data);
  }
});
