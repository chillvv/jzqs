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

App({
  globalData: {
    apiBaseUrl: DEFAULT_API_BASE_URL,
    // 云开发环境ID（与骑手端使用同一个环境）
    cloudEnvId: DEFAULT_CLOUD_ENV_ID,
    serviceHeaders: { ...DEFAULT_SERVICE_HEADERS },
    statusBarHeight: 0,
    navBarHeight: 44
  },
  
  async onLaunch() {
    this.globalData.apiBaseUrl = resolveApiBaseUrl(wx.getStorageSync('apiBaseUrl'));
    this.globalData.cloudEnvId = resolveCloudEnvId(wx.getStorageSync('cloudEnvId'));
    this.globalData.serviceHeaders = resolveServiceHeaders(wx.getStorageSync('serviceHeaders'));
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
      
      // 根据认证状态决定页面跳转
      if (auth.shouldRedirectToAuth()) {
        // 未登录，跳转到个人中心（顾客端通常允许浏览）
        // 实际跳转逻辑由页面自行处理
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
    throw new Error('请使用微信一键登录');
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
