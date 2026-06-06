/**
 * 登录测试页面 - 用于调试
 */

const auth = require('../../utils/auth');
const { DEFAULT_API_BASE_URL } = require('../../utils/api-base');

Page({
  data: {
    phone: '13800138000',
    name: '测试骑手',
    logs: [],
    apiBaseUrl: DEFAULT_API_BASE_URL
  },

  onLoad() {
    const app = getApp();
    this.setData({
      apiBaseUrl: app.globalData.apiBaseUrl
    });
    this.addLog('页面加载完成');
    this.checkAuthState();
  },

  addLog(message) {
    const logs = this.data.logs;
    const time = new Date().toLocaleTimeString();
    logs.unshift(`[${time}] ${message}`);
    this.setData({ logs: logs.slice(0, 20) });
    console.log(message);
  },

  checkAuthState() {
    const app = getApp();
    const authToken = wx.getStorageSync('auth_token');
    
    this.addLog(`认证状态: ${app.globalData.riderAuthReady ? '已就绪' : '未就绪'}`);
    this.addLog(`OpenID: ${auth.globalData.openid || app.globalData.riderOpenid || '无'}`);
    this.addLog(`Token: ${authToken ? '有' : '无'}`);
    this.addLog(`骑手: ${app.globalData.riderName || '未登录'}`);
    this.addLog(`状态: ${app.globalData.riderStatus}`);
  },

  onPhoneInput(e) {
    this.setData({ phone: e.detail.value });
  },

  onNameInput(e) {
    this.setData({ name: e.detail.value });
  },

  // 测试微信登录
  async testWxLogin() {
    this.addLog('开始测试微信登录...');
    try {
      await auth.silentLogin();
      getApp().syncRiderGlobals();
      this.addLog(`微信登录成功，OpenID: ${auth.globalData.openid || '无'}`);
      this.checkAuthState();
    } catch (error) {
      this.addLog(`微信登录失败: ${error.message}`);
    }
  },

  // 测试注册
  async testRegister() {
    const { phone, name } = this.data;
    this.addLog(`开始测试注册: ${name} - ${phone}`);
    
    try {
      const app = getApp();
      await app.waitForRiderAuth();
      const openid = auth.globalData.openid || app.globalData.riderOpenid;
      
      this.addLog(`OpenID: ${openid}`);
      const response = await auth.register(phone, name, openid);
      
      this.addLog(`注册成功: ${JSON.stringify(response)}`);
      app.syncRiderGlobals();
      this.checkAuthState();
    } catch (error) {
      this.addLog(`注册失败: ${error.message}`);
    }
  },

  // 测试手机号登录
  async testPhoneLogin() {
    const { phone } = this.data;
    this.addLog(`开始测试手机号登录: ${phone}`);
    
    try {
      const app = getApp();
      await app.waitForRiderAuth();
      const openid = auth.globalData.openid || app.globalData.riderOpenid;
      
      this.addLog(`OpenID: ${openid}`);
      const response = await auth.phoneLogin(phone, openid);
      
      this.addLog(`登录成功: ${JSON.stringify(response)}`);
      app.syncRiderGlobals();
      this.checkAuthState();
    } catch (error) {
      this.addLog(`登录失败: ${error.message}`);
    }
  },

  // 测试 Token 验证
  async testVerifyToken() {
    this.addLog('开始测试 Token 验证...');
    
    try {
      const authToken = wx.getStorageSync('auth_token');
      if (!authToken) {
        this.addLog('没有 Token');
        return;
      }
      
      const response = await auth.verifyToken(authToken);
      
      this.addLog(`验证成功: ${JSON.stringify(response)}`);
    } catch (error) {
      this.addLog(`验证失败: ${error.message}`);
    }
  },

  // 清除登录态
  async clearAuth() {
    const app = getApp();
    await app.resetRiderAuthState(true);
    this.addLog('登录态已清除');
    this.checkAuthState();
  },

  // 清除日志
  clearLogs() {
    this.setData({ logs: [] });
  }
});
