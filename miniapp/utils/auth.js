/**
 * 统一认证模块 - 顾客端
 * 基于微信小程序统一登录方案
 *
 * @author Kiro AI
 * @since 2026-05-23
 */

const { DEFAULT_API_BASE_URL, resolveApiBaseUrl } = require('./api-base');

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
      needPhoneAuth: false,
      needName: false,
      authMode: 'UNKNOWN'
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
        const result = await this.request('/api/mobile/auth/verify', 'GET', null, token);
        if (result.valid && result.userType === 'customer') {
          this.globalData.token = token;
          this.globalData.userId = result.userId;
          this.globalData.userType = result.userType;
          this.globalData.loggedIn = true;
          this.globalData.ready = true;
          this.globalData.authMode = 'TOKEN';
          this.syncAppGlobalData();
          return;
        }
      } catch (e) {
        // token 无效，继续走登录流
        console.log('[Auth] Token 验证失败:', e.message);
      }
      wx.removeStorageSync(AUTH_TOKEN_KEY);
    }

    // 3. 无有效 token，走微信静默登录
    try {
      await this.silentLogin();
    } catch (error) {
      console.error('[Auth] 静默登录失败，进入未登录状态', error);
      this.syncAppGlobalData();
    }
    this.globalData.ready = true;
    this.syncAppGlobalData();
  }

  /**
   * 微信静默登录（wx.login → code → 后端换 openid）
   */
  async silentLogin() {
    try {
      const { code } = await wx.login();
      const result = await this.request('/api/mobile/auth/wx-login', 'POST', {
        code
      });

      this.applyAuthState(result);
      return result;
    } catch (error) {
      console.error('[Auth] 静默登录失败:', error);
      throw error;
    }
  }

  async ensureOpenidReady() {
    if (this.globalData.openid) {
      return this.globalData.openid;
    }
    await this.silentLogin();
    if (!this.globalData.openid) {
      throw new Error('缺少登录身份标识，请重新进入小程序后重试');
    }
    return this.globalData.openid;
  }

  /**
   * 手机号登录
   */
  async phoneLogin(phone) {
    try {
      const openid = await this.ensureOpenidReady();
      const result = await this.request('/api/mobile/auth/phone-login', 'POST', {
        openid,
        phone
      });
      this.applyAuthState(result);
      return result;
    } catch (error) {
      console.error('[Auth] 手机号登录失败:', error);
      throw error;
    }
  }

  /**
   * 手动注册并绑定手机号
   */
  async register(phone, nickname) {
    try {
      const openid = await this.ensureOpenidReady();
      const result = await this.request('/api/mobile/auth/register', 'POST', {
        openid,
        phone,
        nickname
      });
      this.applyAuthState(result);
      return result;
    } catch (error) {
      console.error('[Auth] 顾客注册失败:', error);
      throw error;
    }
  }

  /**
   * 完成顾客首次资料补全
   */
  async completeProfile(nickname) {
    try {
      const openid = await this.ensureOpenidReady();
      const result = await this.request('/api/mobile/auth/complete-profile', 'POST', {
        openid,
        nickname
      });
      this.applyAuthState(result);
      return result;
    } catch (error) {
      console.error('[Auth] 完善资料失败:', error);
      throw error;
    }
  }

  /**
   * 手机号快捷登录
   */
  async bindPhone(payload) {
    if (!payload || typeof payload !== 'object') {
      throw new Error('请重新发起手机号授权');
    }
    const code = String(payload.code || '').trim();
    if (!code) {
      throw new Error('手机号授权失败，请重试');
    }
    try {
      const openid = await this.ensureOpenidReady();
      const result = await this.request('/api/mobile/auth/bind-phone', 'POST', {
        code,
        openid
      });
      this.applyAuthState(result);
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
    this.globalData.authMode = result.authMode || 'MINIAPP_WX';
    this.globalData.openid = result.openid || '';
    this.globalData.registered = result.registered || false;
    this.globalData.needName = Boolean(result.needName);
    this.globalData.needPhoneAuth = Boolean(result.needPhoneAuth || (!result.registered && !this.globalData.needName));

    if (result.token) {
      this.applyAuth(result);
      return;
    }
    this.syncAppGlobalData();
  }

  /**
   * 应用认证结果（登录成功）
   */
  applyAuth(result) {
    if (result.token) {
      wx.setStorageSync(AUTH_TOKEN_KEY, result.token);
      this.globalData.token = result.token;
      this.globalData.userId = result.userId || result.customerId || null;
      this.globalData.userType = result.userType || 'customer';
      this.globalData.loggedIn = true;
      this.globalData.registered = true;
      this.globalData.needPhoneAuth = false;
      this.globalData.needName = Boolean(result.needName);
      this.globalData.authMode = result.authMode || this.globalData.authMode;
      this.syncAppGlobalData();
    }
  }

  /**
   * 退出登录
   */
  async logout() {
    const token = this.globalData.token || wx.getStorageSync(AUTH_TOKEN_KEY);

    try {
      if (token) {
        await this.request('/api/mobile/auth/logout', 'POST', {}, token);
      }
    } catch (error) {
      console.warn('[Auth] 退出登录请求失败:', error);
    } finally {
      wx.removeStorageSync(AUTH_TOKEN_KEY);
      wx.removeStorageSync(AUTH_STATE_KEY);
      this.globalData.token = null;
      this.globalData.userId = null;
      this.globalData.openid = '';
      this.globalData.loggedIn = false;
      this.globalData.registered = false;
      this.globalData.needPhoneAuth = false;
      this.globalData.needName = false;
      this.globalData.authMode = 'UNKNOWN';
      this.globalData.ready = true;
      this.syncAppGlobalData();
    }
  }

  syncAppGlobalData() {
    try {
      const app = getApp();
      if (!app || !app.globalData) {
        return;
      }
      app.globalData.token = this.globalData.token;
      app.globalData.loggedIn = this.globalData.loggedIn;
      app.globalData.needPhoneAuth = this.globalData.needPhoneAuth;
      app.globalData.needName = this.globalData.needName;
      app.globalData.registered = this.globalData.registered;
      app.globalData.openid = this.globalData.openid;
      app.globalData.userId = this.globalData.userId;
      app.globalData.userType = this.globalData.userType;
      app.globalData.authMode = this.globalData.authMode;
    } catch (error) {
      // getApp 在极早期时可能尚未完全可用，这里静默跳过即可
    }
  }

  /**
   * 封装的请求，自动带 token
   */
  async request(url, method, data, customToken) {
    const self = this;
    const token = customToken || this.globalData.token;
    return new Promise((resolve, reject) => {
      const app = typeof getApp === 'function' ? getApp() : null;
      const apiBaseUrl = app && app.globalData
        ? resolveApiBaseUrl(app.globalData.apiBaseUrl)
        : resolveApiBaseUrl(wx.getStorageSync('apiBaseUrl') || DEFAULT_API_BASE_URL);
      wx.request({
        url: apiBaseUrl + url,
        method,
        data,
        header: token ? { Authorization: 'Bearer ' + token } : {},
        success(res) {
          const body = res.data || {};
          if (body.code === 'UNAUTHORIZED') {
            // 401:清除 token 与登录态并同步到 app，让调用方处理跳转
            wx.removeStorageSync(AUTH_TOKEN_KEY);
            self.globalData.token = null;
            self.globalData.loggedIn = false;
            self.syncAppGlobalData();
            reject(new Error(body.message || '登录状态已失效'));
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
      needName: this.globalData.needName,
      openid: this.globalData.openid,
      userId: this.globalData.userId,
      userType: this.globalData.userType,
      authMode: this.globalData.authMode
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
