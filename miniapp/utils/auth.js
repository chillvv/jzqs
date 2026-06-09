/**
 * 缁熶竴璁よ瘉妯″潡 - 椤惧绔?
 * 鍩轰簬寰俊灏忕▼搴忕粺涓€鐧诲綍鏂规
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
      needPhoneAuth: false,
      authMode: 'UNKNOWN'
    };
  }

  /**
   * 鍒濆鍖栬璇佺姸鎬?
   * 灏忕▼搴忓惎鍔ㄦ椂璋冪敤涓€娆?
   */
  async init() {
    // 1. 妫€鏌ユ湰鍦?token
    const token = wx.getStorageSync(AUTH_TOKEN_KEY);

    if (token) {
      // 2. 楠岃瘉 token 鏄惁鏈夋晥
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
        // token 鏃犳晥锛岀户缁蛋鐧诲綍娴?
        console.log('[Auth] Token 楠岃瘉澶辫触:', e.message);
      }
      wx.removeStorageSync(AUTH_TOKEN_KEY);
    }

    // 3. 鏃犳湁鏁?token锛岃蛋寰俊闈欓粯鐧诲綍
    try {
      await this.silentLogin();
    } catch (error) {
      console.error('[Auth] 闈欓粯鐧诲綍澶辫触锛岃繘鍏ユ湭鐧诲綍鐘舵€?', error);
      this.syncAppGlobalData();
    }
    this.globalData.ready = true;
    this.syncAppGlobalData();
  }

  /**
   * 寰俊闈欓粯鐧诲綍锛坵x.login 鈫?code 鈫?鍚庣鎹?openid锛?
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
      console.error('[Auth] 闈欓粯鐧诲綍澶辫触:', error);
      throw error;
    }
  }

  /**
   * 鎵嬫満鍙风櫥褰?
   */
  async phoneLogin(phone) {
    try {
      const result = await this.request('/api/mobile/auth/phone-login', 'POST', {
        openid: this.globalData.openid,
        phone
      });
      this.applyAuthState(result);
      return result;
    } catch (error) {
      console.error('[Auth] 鎵嬫満鍙风櫥褰曞け璐?', error);
      throw error;
    }
  }

  /**
   * 鎵嬪姩娉ㄥ唽骞剁粦瀹氭墜鏈哄彿
   */
  async register(phone, nickname) {
    try {
      const result = await this.request('/api/mobile/auth/register', 'POST', {
        openid: this.globalData.openid,
        phone,
        nickname
      });
      this.applyAuthState(result);
      return result;
    } catch (error) {
      console.error('[Auth] 椤惧娉ㄥ唽澶辫触:', error);
      throw error;
    }
  }

  /**
   * 瀹屾垚椤惧棣栨璧勬枡琛ュ叏
   */
  async completeProfile(nickname) {
    try {
      const result = await this.request('/api/mobile/auth/complete-profile', 'POST', {
        openid: this.globalData.openid,
        nickname
      });
      this.applyAuthState(result);
      return result;
    } catch (error) {
      console.error('[Auth] 瀹屽杽璧勬枡澶辫触:', error);
      throw error;
    }
  }

  /**
   * 微信手机号一键登录
   */
  async bindPhone(payload) {
    if (!payload || typeof payload !== 'object') {
      throw new Error('请重新发起微信手机号授权');
    }
    const code = String(payload.code || '').trim();
    if (!code) {
      throw new Error('微信手机号授权失败，请重试');
    }
    try {
      const result = await this.request('/api/mobile/auth/bind-phone', 'POST', {
        code,
        openid: this.globalData.openid
      });
      this.applyAuthState(result);
      return result;
    } catch (error) {
      console.error('[Auth] 缁戝畾鎵嬫満鍙峰け璐?', error);
      throw error;
    }
  }

  /**
   * 搴旂敤璁よ瘉鐘舵€侊紙闈欓粯鐧诲綍缁撴灉锛?
   */
  applyAuthState(result) {
    this.globalData.authMode = result.authMode || 'MINIAPP_WX';
    this.globalData.openid = result.openid || '';
    this.globalData.registered = result.registered || false;
    this.globalData.needPhoneAuth = Boolean(result.needPhoneAuth || !result.registered);

    if (result.token) {
      this.applyAuth(result);
      return;
    }
    this.syncAppGlobalData();
  }

  /**
   * 搴旂敤璁よ瘉缁撴灉锛堢櫥褰曟垚鍔燂級
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
      this.globalData.authMode = result.authMode || this.globalData.authMode;
      this.syncAppGlobalData();
    }
  }

  /**
   * 閫€鍑虹櫥褰?
   */
  async logout() {
    const token = this.globalData.token || wx.getStorageSync(AUTH_TOKEN_KEY);

    try {
      if (token) {
        await this.request('/api/mobile/auth/logout', 'POST', {}, token);
      }
    } catch (error) {
      console.warn('[Auth] 閫€鍑虹櫥褰曡姹傚け璐?', error);
    } finally {
      wx.removeStorageSync(AUTH_TOKEN_KEY);
      wx.removeStorageSync(AUTH_STATE_KEY);
      this.globalData.token = null;
      this.globalData.userId = null;
      this.globalData.openid = '';
      this.globalData.loggedIn = false;
      this.globalData.registered = false;
      this.globalData.needPhoneAuth = false;
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
      app.globalData.registered = this.globalData.registered;
      app.globalData.openid = this.globalData.openid;
      app.globalData.userId = this.globalData.userId;
      app.globalData.userType = this.globalData.userType;
      app.globalData.authMode = this.globalData.authMode;
    } catch (error) {
      // getApp 鍦ㄦ瀬鏃╂湡鏃跺彲鑳藉皻鏈畬鍏ㄥ彲鐢紝杩欓噷闈欓粯璺宠繃鍗冲彲
    }
  }

  /**
   * 灏佽鐨勮姹傦紝鑷姩甯?token
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
            // 401:清除状态，但不要在这里直接跳转，让调用方处理
            wx.removeStorageSync(AUTH_TOKEN_KEY);
            reject(new Error(body.message || '登录状态已失效'));
            return;
          }
          if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
            resolve(body.data);
            return;
          }
          reject(new Error(body.message || '璇锋眰澶辫触'));
        },
        fail() {
          reject(new Error('鏆傛椂鏃犳硶杩炴帴鏈嶅姟'));
        }
      });
    });
  }

  /**
   * 绛夊緟璁よ瘉灏辩华
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
   * 鑾峰彇璁よ瘉鐘舵€?
   */
  getAuthState() {
    return {
      ready: this.globalData.ready,
      loggedIn: this.globalData.loggedIn,
      registered: this.globalData.registered,
      needPhoneAuth: this.globalData.needPhoneAuth,
      openid: this.globalData.openid,
      userId: this.globalData.userId,
      userType: this.globalData.userType,
      authMode: this.globalData.authMode
    };
  }

  /**
   * 鍒ゆ柇鏄惁闇€瑕佽烦杞埌鐧诲綍/娉ㄥ唽椤甸潰
   */
  shouldRedirectToAuth() {
    return this.globalData.ready && !this.globalData.loggedIn;
  }

  /**
   * 鍒ゆ柇鏄惁闇€瑕佺粦瀹氭墜鏈哄彿
   */
  shouldBindPhone() {
    return this.globalData.ready && !this.globalData.registered && this.globalData.needPhoneAuth;
  }
}

// 鍒涘缓鍗曚緥
const auth = new Auth();

module.exports = auth;
