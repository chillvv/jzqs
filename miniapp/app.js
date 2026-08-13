const auth = require('./utils/auth');
const {
  requestCombinedSubscribeAuthorization,
  cacheDeliveryAcceptResult,
  saveNightlySubscription
} = require('./utils/delivery-subscription');
const {
  DEFAULT_API_BASE_URL,
  DEFAULT_CLOUD_ENV_ID,
  DEFAULT_SERVICE_HEADERS,
  resolveApiBaseUrl,
  resolveCloudEnvId,
  resolveServiceHeaders
} = require('./utils/api-base');
const realtime = require('./utils/realtime');
const { isTestEnv } = require('./utils/env');
const onboarding = require('./utils/onboarding');

App({
  globalData: {
    apiBaseUrl: DEFAULT_API_BASE_URL,
    // 云开发环境ID（与骑手端使用同一个环境）
    cloudEnvId: DEFAULT_CLOUD_ENV_ID,
    serviceHeaders: { ...DEFAULT_SERVICE_HEADERS },
    statusBarHeight: 0,
    navBarHeight: 44,
    // 是否测试环境（开发版/体验版=true，正式版=false），用于决定是否展示内部测试入口
    isTestEnv: false
  },
  
  async onLaunch() {
    try {
      wx.showShareMenu({ menus: ['shareAppMessage', 'shareTimeline'] });
    } catch (e) {
      console.error('[App] 初始化分享菜单失败', e);
    }

    this.globalData.apiBaseUrl = resolveApiBaseUrl(wx.getStorageSync('apiBaseUrl'));
    this.globalData.cloudEnvId = resolveCloudEnvId(wx.getStorageSync('cloudEnvId'));
    this.globalData.serviceHeaders = resolveServiceHeaders(wx.getStorageSync('serviceHeaders'));
    this.globalData.isTestEnv = isTestEnv();
    realtime.init({
      clientLabel: 'customer',
      getToken: () => wx.getStorageSync('auth_token') || auth.globalData.token || ''
    });
    
    // 获取设备信息以计算自定义导航栏高度
    try {
      const systemInfo = wx.getSystemInfoSync();
      const menuButton = wx.getMenuButtonBoundingClientRect();
      
      this.globalData.statusBarHeight = systemInfo.statusBarHeight || 20;
      
      // 导航栏总高度 = 胶囊按钮底部位置 + 胶囊按钮下方的间距
      // 胶囊按钮下方间距 = 胶囊按钮距离顶部 - 状态栏高度
      const gap = menuButton.top - this.globalData.statusBarHeight;
      this.globalData.navBarHeight = menuButton.top + menuButton.height + gap;
    } catch (e) {
      console.error('[App] 获取设备信息失败', e);
    }
    
    // 初始化云开发（用于查看骑手上传的照片）
    if (wx.cloud && this.globalData.cloudEnvId) {
      wx.cloud.init({
        env: this.globalData.cloudEnvId,
        traceUser: true
      });
      console.log('[云开发] 初始化成功', this.globalData.cloudEnvId);
    }
    
    // 初始化统一认证
    try {
      await auth.init();
      if (!auth.shouldRedirectToAuth()) {
        // 注意：微信要求 wx.requestSubscribeMessage 必须由「用户点击」行为触发，
        // 不能在登录/启动等自动流程中静默调用（会被微信拦截，返回 fail "can only be
        // invoked by user TAP gesture"）。每晚用餐提醒的授权改由「个人中心」页面的
        // 按钮显式触发（见 pages/profile/index.js -> enableNightlyReminder）。
        // 首次登录自动开始新手指引（已跳过过的不会再弹）
        onboarding.maybeAutoStart();
      }
    } catch (error) {
      console.error('[App] 认证初始化失败:', error);
      wx.showToast({ title: '登录失败，请稍后重试', icon: 'none' });
    }
  },
  
  /**
   * 等待认证就绪
   */
  waitForAuth() {
    return auth.waitForAuth();
  },

  /**
   * 注意：不再提供自动拉起订阅授权的方法。
   * 微信规定 wx.requestSubscribeMessage 必须由用户点击触发，故每晚用餐提醒的
   * 授权仅能在「个人中心」页面用户点击「开启每晚用餐提醒」按钮时执行
   * （见 pages/profile/index.js -> enableNightlyReminder）。
   */

  /**
   * 获取认证状态
   */
  getAuthState() {
    return auth.getAuthState();
  },
  
  /**
   * 处理 401 未授权
   */
  handleUnauthorized() {
    auth.logout();
    wx.redirectTo({ url: '/pages/profile/index' });
  },
  
  /**
   * 提交手机号认证（兼容旧版）
   */
  async submitPhoneAuth({ phoneNumber, nickname }) {
    // 注意：新版使用微信一键登录，此方法仅作兼容
    throw new Error('请使用手机号快捷登录');
  },
  
  /**
   * 通用请求方法（兼容旧版）
   */
  postJson(path, data) {
    return auth.request(path, 'POST', data);
  },
  
  /**
   * 通用 GET 请求方法
   */
  getJson(path) {
    return auth.request(path, 'GET');
  }
});
