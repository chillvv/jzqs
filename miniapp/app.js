const auth = require('./utils/auth');
const { DEFAULT_API_BASE_URL, resolveApiBaseUrl } = require('./utils/api-base');

App({
  globalData: {
    apiBaseUrl: DEFAULT_API_BASE_URL,
    // 云开发环境ID（与骑手端使用同一个环境）
    cloudEnvId: 'cloud1-4g88w3e2dedee471'
  },
  
  async onLaunch() {
    this.globalData.apiBaseUrl = resolveApiBaseUrl(wx.getStorageSync('apiBaseUrl'));
    
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
