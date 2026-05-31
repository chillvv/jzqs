/**
 * 统一认证模块 - 顾客端
 * 基于微信小程序统一登录方案
 * 
 * @author Kiro AI
 * @since 2026-05-23
 */

const AUTH_TOKEN_KEY = 'auth_token';
const AUTH_STATE_KEY = 'auth_state';

class Auth {
  constructor() {
    this.globalData = {
      ready: false,
      token: null,
      userId: null,
      userType: 'customer',
      loggedIn: false,
      openid: '',
      registered: false,
      needPhoneAuth: false
    };
  }

  /**
   * 初始化认证状态
   * 小程序启动时调用一次
   */
  async init() {
    // 1. 检查本地 token
    const token = wx.getStorageSync(AUTH_TOKEN_KEY);
    
    if (token) {
      // 2. 验证 token 是否有效
      try {
        const result = await this.request('/api/auth/verify', 'GET', null, token);
        if (result.valid && result.userType === 'customer') {
          this.globalData.token = token;
          this.globalData.userId = result.userId;
          this.globalData.userType = result.userType;
          this.globalData.loggedIn = true;
          this.globalData.ready = true;
          return;
        }
      } catch (e) {
        // token 无效，继续走登录流
        console.log('[Auth] Token 验证失败:', e.message);
      }
      wx.removeStorageSync(AUTH_TOKEN_KEY);
    }

    // 3. 无有效 token，走微信静默登录
    await this.silentLogin();
    this.globalData.ready = true;
  }

  /**
   * 微信静默登录（wx.login → code → 后端换 openid）
   */
  async silentLogin() {
    try {
      const { code } = await wx.login();
      const result = await this.request('/api/auth/login', 'POST', { 
        code, 
        userType: 'customer' 
      });
      
      this.applyAuthState(result);
      return result;
    } catch (error) {
      console.error('[Auth] 静默登录失败:', error);
      throw error;
    }
  }

  /**
   * 微信一键登录（绑定手机号）- 新版 API
   * @param {string} code getPhoneNumber 返回的动态令牌
   */
  async bindPhone(code) {
    try {
      const result = await this.request('/api/auth/bind-phone', 'POST', {
        code,
        userType: 'customer'
      });
      
      this.applyAuth(result);
      return result;
    } catch (error) {
      console.error('[Auth] 绑定手机号失败:', error);
      throw error;
    }
  }

  /**
   * 应用认证状态（静默登录结果）
   */
  applyAuthState(result) {
    this.globalData.openid = result.openid || '';
    this.globalData.registered = result.registered || false;
    this.globalData.needPhoneAuth = !result.registered;
    
    if (result.token) {
      this.applyAuth(result);
    }
  }

  /**
   * 应用认证结果（登录成功）
   */
  applyAuth(result) {
    if (result.token) {
      wx.setStorageSync(AUTH_TOKEN_KEY, result.token);
      this.globalData.token = result.token;
      this.globalData.userId = result.userId;
      this.globalData.userType = result.userType;
      this.globalData.loggedIn = true;
      this.globalData.registered = true;
      this.globalData.needPhoneAuth = false;
    }
  }

  /**
   * 退出登录
   */
  async logout() {
    const token = this.globalData.token || wx.getStorageSync(AUTH_TOKEN_KEY);

    try {
      if (token) {
        await this.request('/api/auth/logout', 'POST', {}, token);
      }
    } catch (error) {
      console.warn('[Auth] 退出登录请求失败:', error);
    } finally {
      wx.removeStorageSync(AUTH_TOKEN_KEY);
      wx.removeStorageSync(AUTH_STATE_KEY);
      this.globalData.token = null;
      this.globalData.userId = null;
      this.globalData.loggedIn = false;
      this.globalData.registered = false;
      this.globalData.ready = true;
    }
  }

  /**
   * 封装的请求，自动带 token
   */
  async request(url, method, data, customToken) {
    const token = customToken || this.globalData.token;
    return new Promise((resolve, reject) => {
      wx.request({
        url: getApp().globalData.apiBaseUrl + url,
        method,
        data,
        header: token ? { Authorization: 'Bearer ' + token } : {},
        success(res) {
          const body = res.data || {};
          if (body.code === 'UNAUTHORIZED') {
            // 401：清除状态，跳登录
            wx.removeStorageSync(AUTH_TOKEN_KEY);
            wx.redirectTo({ url: '/pages/profile/index' });
            reject(new Error(body.message));
            return;
          }
          if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
            resolve(body.data);
            return;
          }
          reject(new Error(body.message || '请求失败'));
        },
        fail() {
          reject(new Error('暂时无法连接服务'));
        }
      });
    });
  }

  /**
   * 等待认证就绪
   */
  waitForAuth() {
    return new Promise((resolve) => {
      const check = () => {
        if (this.globalData.ready) {
          resolve();
        } else {
          setTimeout(check, 100);
        }
      };
      check();
    });
  }

  /**
   * 获取认证状态
   */
  getAuthState() {
    return {
      ready: this.globalData.ready,
      loggedIn: this.globalData.loggedIn,
      registered: this.globalData.registered,
      needPhoneAuth: this.globalData.needPhoneAuth,
      openid: this.globalData.openid,
      userId: this.globalData.userId,
      userType: this.globalData.userType
    };
  }

  /**
   * 判断是否需要跳转到登录/注册页面
   */
  shouldRedirectToAuth() {
    return this.globalData.ready && !this.globalData.loggedIn;
  }

  /**
   * 判断是否需要绑定手机号
   */
  shouldBindPhone() {
    return this.globalData.ready && !this.globalData.registered && this.globalData.needPhoneAuth;
  }
}

// 创建单例
const auth = new Auth();

module.exports = auth;
