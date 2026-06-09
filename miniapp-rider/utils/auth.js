/**
 * 统一认证模块 - 骑手端
 * 基于骑手手机号登录方案
 * 
 * @author Kiro AI
 * @since 2026-05-23
 */

const AUTH_TOKEN_KEY = 'auth_token';
const AUTH_STATE_KEY = 'auth_state';
const RIDER_VERIFY_TOKEN_URL = '/api/mobile/rider-auth/verify-token';
const RIDER_PROFILE_URL = '/api/mobile/rider-auth/me';

function reportAuthDebug(hypothesisId, location, msg, data, traceId) {
  void hypothesisId;
  void location;
  void msg;
  void data;
  void traceId;
}

class Auth {
  constructor() {
    this.globalData = {
      ready: false,
      token: null,
      userId: null,
      userType: 'rider',
      loggedIn: false,
      openid: '',
      registered: false,
      needPhoneAuth: false,
      riderStatus: 'UNAUTHORIZED',
      workbenchEnabled: false,
      riderName: '',
      phone: ''
    };
  }

  /**
   * 初始化认证状态
   * 小程序启动时调用一次
   */
  async init() {
    // 1. 检查本地 token
    const token = wx.getStorageSync(AUTH_TOKEN_KEY);
    // #region debug-point A:init-token
    reportAuthDebug('A', 'auth.js:init', 'Auth.init start', {
      hasToken: !!token,
      ready: this.globalData.ready,
      loggedIn: this.globalData.loggedIn
    });
    // #endregion
    
    if (token) {
      // 2. 验证 token 是否有效
      try {
        const result = await this.verifyToken(token);
        if (result && result.riderId) {
          this.applyVerifiedAuthState(token, result);
          this.globalData.ready = true;
          return;
        }
      } catch (e) {
        // token 无效，继续走登录流
        console.log('[Auth] Token 验证失败:', e.message);
      }
      wx.removeStorageSync(AUTH_TOKEN_KEY);
    }

    this.globalData.ready = true;
  }

  /**
   * 手动手机号登录（骑手端专用）
   * @param {string} phone 手机号
   * @param {string} openid 微信 openid（可选）
   */
  async phoneLogin(phone, openid) {
    try {
      const result = await this.request('/api/auth/phone-login', 'POST', {
        phone,
        openid: openid || this.globalData.openid,
        userType: 'rider'
      });
      
      if (!result.token) {
        throw new Error(result.message || '登录失败');
      }
      
      this.applyAuth(result);
      this.globalData.userId = result.userId || result.riderId || this.globalData.userId;
      this.globalData.userType = 'rider';
      this.globalData.loggedIn = true;
      this.globalData.registered = true;
      this.globalData.needPhoneAuth = false;
      this.globalData.riderStatus = result.riderStatus || result.status || 'ACTIVE';
      this.globalData.workbenchEnabled = typeof result.workbenchEnabled === 'boolean'
        ? result.workbenchEnabled
        : this.globalData.riderStatus === 'ACTIVE';
      this.globalData.riderName = result.riderName || result.name || '';
      this.globalData.phone = result.phone || phone;
      this.globalData.ready = true;
      
      return result;
    } catch (error) {
      console.error('[Auth] 手机号登录失败:', error);
      throw error;
    }
  }

  /**
   * 骑手注册
   * @param {string} phone 手机号
   * @param {string} name 姓名
   * @param {string} openid 微信 openid（可选）
   */
  async register(phone, name, openid) {
    try {
      const result = await this.request('/api/auth/register-phone', 'POST', {
        phone,
        nickname: name,
        openid: openid || this.globalData.openid,
        userType: 'rider'
      });
      
      if (!result.token) {
        throw new Error(result.message || '注册失败');
      }
      
      this.applyAuth(result);
      this.globalData.userId = result.userId || result.riderId || this.globalData.userId;
      this.globalData.userType = 'rider';
      this.globalData.loggedIn = true;
      this.globalData.registered = true;
      this.globalData.needPhoneAuth = false;
      this.globalData.riderStatus = result.riderStatus || result.status || 'PENDING';
      this.globalData.workbenchEnabled = typeof result.workbenchEnabled === 'boolean'
        ? result.workbenchEnabled
        : this.globalData.riderStatus === 'ACTIVE';
      this.globalData.riderName = result.riderName || result.name || name;
      this.globalData.phone = result.phone || phone;
      this.globalData.ready = true;
      
      return result;
    } catch (error) {
      console.error('[Auth] 骑手注册失败:', error);
      throw error;
    }
  }

  /**
   * 应用认证结果（登录成功）
   */
  applyAuth(result) {
    if (result.token) {
      wx.setStorageSync(AUTH_TOKEN_KEY, result.token);
      this.globalData.token = result.token;
      this.globalData.userId = result.userId || result.riderId || null;
      this.globalData.userType = result.userType || this.globalData.userType;
      this.globalData.loggedIn = true;
      this.globalData.registered = true;
      this.globalData.needPhoneAuth = false;
      this.globalData.riderStatus = result.status || result.riderStatus || this.globalData.riderStatus;
      this.globalData.workbenchEnabled = typeof result.workbenchEnabled === 'boolean'
        ? result.workbenchEnabled
        : this.globalData.riderStatus === 'ACTIVE';
      this.globalData.riderName = result.name || result.riderName || this.globalData.riderName;
      this.globalData.phone = result.phone || '';
    }
  }

  /**
   * 应用 token 校验后的认证结果
   */
  applyVerifiedAuthState(token, result) {
    this.globalData.token = token;
    this.globalData.userId = result.riderId || null;
    this.globalData.userType = 'rider';
    this.globalData.loggedIn = true;
    this.globalData.registered = true;
    this.globalData.needPhoneAuth = false;
    this.globalData.openid = result.openid || '';
    this.globalData.riderStatus = result.status || result.riderStatus || 'UNAUTHORIZED';
    this.globalData.workbenchEnabled = typeof result.workbenchEnabled === 'boolean'
      ? result.workbenchEnabled
      : this.globalData.riderStatus === 'ACTIVE';
    this.globalData.riderName = result.name || result.riderName || '';
    this.globalData.phone = result.phone || '';
  }

  /**
   * 校验骑手 token
   */
  async verifyToken(token) {
    return this.request(RIDER_VERIFY_TOKEN_URL, 'POST', { token });
  }

  /**
   * 加载骑手详细信息
   */
  async loadRiderProfile() {
    try {
      const riderName = this.globalData.riderName;
      if (!riderName) {
        return;
      }
      
      const profile = await this.request(`${RIDER_PROFILE_URL}?riderName=${encodeURIComponent(riderName)}`, 'GET');
      
      this.globalData.riderStatus = profile.riderStatus;
      this.globalData.workbenchEnabled = profile.workbenchEnabled;
      this.globalData.riderName = profile.riderName;
      this.globalData.phone = profile.phone;
    } catch (error) {
      console.error('[Auth] 加载骑手信息失败:', error);
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
      console.warn('[Auth] 骑手退出登录请求失败:', error);
    } finally {
      wx.removeStorageSync(AUTH_TOKEN_KEY);
      wx.removeStorageSync(AUTH_STATE_KEY);
      this.globalData.token = null;
      this.globalData.userId = null;
      this.globalData.loggedIn = false;
      this.globalData.registered = false;
      this.globalData.ready = true;
      this.globalData.riderStatus = 'UNAUTHORIZED';
      this.globalData.workbenchEnabled = false;
      this.globalData.riderName = '';
      this.globalData.phone = '';
      this.globalData.openid = '';
    }
  }

  /**
   * 封装的请求，自动带 token
   */
  async request(url, method, data, customToken) {
    const token = customToken || this.globalData.token;
    const fullUrl = getApp().globalData.apiBaseUrl + url;
    const traceId = `auth-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
    
    console.log('[Auth Request] 开始请求');
    console.log('[Auth Request] URL:', fullUrl);
    console.log('[Auth Request] Method:', method);
    console.log('[Auth Request] Data:', JSON.stringify(data));
    console.log('[Auth Request] Token:', token ? '有' : '无');
    
    return new Promise((resolve, reject) => {
      // #region debug-point C:request-start
      reportAuthDebug('C', 'auth.js:request:start', 'Auth.request start', {
        url,
        fullUrl,
        method,
        hasToken: !!token,
        dataKeys: data ? Object.keys(data) : [],
        hasCode: !!(data && data.code),
        userType: data && data.userType ? data.userType : ''
      }, traceId);
      // #endregion
      wx.request({
        url: fullUrl,
        method,
        data,
        header: token ? { Authorization: 'Bearer ' + token } : {},
        timeout: 10000, // 10秒超时
        success(res) {
          console.log('[Auth Request] 响应状态码:', res.statusCode);
          console.log('[Auth Request] 响应数据:', JSON.stringify(res.data));
          // #region debug-point D:request-success
          const body = res.data || {};
          reportAuthDebug('D', 'auth.js:request:success', 'Auth.request success callback', {
            statusCode: res.statusCode,
            bodyCode: body.code || '',
            bodyMessage: body.message || '',
            hasData: !!body.data,
            dataKeys: body.data ? Object.keys(body.data) : []
          }, traceId);
          // #endregion
          
          if (body.code === 'UNAUTHORIZED') {
            // 401：清除状态
            wx.removeStorageSync(AUTH_TOKEN_KEY);
            reject(new Error(body.message || '未授权'));
            return;
          }
          if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
            resolve(body.data);
            return;
          }
          reject(new Error(body.message || '请求失败'));
        },
        fail(err) {
          console.error('[Auth Request] 请求失败:', err);
          // #region debug-point E:request-fail
          reportAuthDebug('E', 'auth.js:request:fail', 'Auth.request fail callback', {
            errMsg: err && err.errMsg ? err.errMsg : '',
            errno: err && err.errno ? err.errno : '',
            hasTimeout: !!(err && err.errMsg && err.errMsg.includes('timeout'))
          }, traceId);
          // #endregion
          if (err.errMsg && err.errMsg.includes('timeout')) {
            reject(new Error('请求超时，请检查网络'));
          } else {
            reject(new Error('暂时无法连接服务'));
          }
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
      userId: this.globalData.userId,
      userType: this.globalData.userType,
      riderStatus: this.globalData.riderStatus,
      workbenchEnabled: this.globalData.workbenchEnabled,
      riderName: this.globalData.riderName,
      phone: this.globalData.phone
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

  /**
   * 判断是否可以进入工作台
   */
  canUseWorkbench() {
    return this.globalData.ready && 
           this.globalData.loggedIn && 
           this.globalData.registered && 
           this.globalData.workbenchEnabled;
  }

  /**
   * 获取骑手入口页面
   */
  getRiderEntryPage() {
    if (!this.globalData.ready) {
      return null;
    }
    
    if (!this.globalData.loggedIn) {
      return '/pages/profile/index';
    }

    if (!this.globalData.registered) {
      return '/pages/profile/index';
    }

    switch (this.globalData.riderStatus) {
      case 'ACTIVE':
        return '/pages/today/index';
      case 'PENDING':
        return '/pages/pending/index';
      case 'DISABLED':
        return '/pages/blocked/index';
      case 'NOT_FOUND':
        return '/pages/register/index';
      default:
        return '/pages/profile/index';
    }
  }

  /**
   * 确保可以访问工作台
   */
  async ensureWorkbenchAccess() {
    await this.waitForAuth();
    if (this.canUseWorkbench()) {
      return true;
    }
    return false;
  }

  /**
   * 获取工作台阻止消息
   */
  getWorkbenchBlockMessage() {
    if (!this.globalData.registered) {
      return '请先登录骑手账号';
    }
    
    switch (this.globalData.riderStatus) {
      case 'PENDING':
        return '当前账号待分配配送区域';
      case 'DISABLED':
        return '当前骑手账号已停用';
      case 'NOT_FOUND':
        return '后台未开通该手机号对应的骑手账号';
      default:
        return '当前账号暂不可用';
    }
  }
}

// 创建单例
const auth = new Auth();

module.exports = auth;
