const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const pagePath = path.join(__dirname, '..', 'pages', 'profile', 'index.js');

function loadPage({ requestHandler } = {}) {
  let pageConfig = null;
  const requestCalls = [];
  const storage = new Map();
  const app = {
    globalData: {
      apiBaseUrl: 'http://localhost:8081',
      token: null,
      authMode: 'WECHAT',
      needPhoneAuth: true
    },
    waitForAuth() {
      return Promise.resolve();
    }
  };

  global.Page = (config) => {
    pageConfig = config;
  };

  global.getApp = () => app;

  global.wx = {
    request(options) {
      requestCalls.push(options);
      return requestHandler(options);
    },
    showToast() {},
    showModal() {},
    stopPullDownRefresh() {},
    setStorageSync(key, value) {
      storage.set(key, value);
    },
    getStorageSync(key) {
      return storage.get(key);
    },
    removeStorageSync(key) {
      storage.delete(key);
    }
  };

  const authModulePath = path.join(__dirname, '..', 'utils', 'auth.js');
  delete require.cache[require.resolve(authModulePath)];
  const auth = require(authModulePath);
  auth.globalData.openid = 'openid-1';
  auth.globalData.token = null;
  auth.globalData.loggedIn = false;
  auth.globalData.registered = false;
  auth.globalData.needPhoneAuth = true;
  auth.globalData.ready = true;

  delete require.cache[require.resolve(pagePath)];
  require(pagePath);

  const page = {
    data: JSON.parse(JSON.stringify(pageConfig.data)),
    setData(patch) {
      for (const [key, value] of Object.entries(patch)) {
        if (key.includes('.')) {
          const parts = key.split('.');
          let cursor = this.data;
          while (parts.length > 1) {
            const part = parts.shift();
            cursor[part] = cursor[part] || {};
            cursor = cursor[part];
          }
          cursor[parts[0]] = value;
        } else {
          this.data[key] = value;
        }
      }
    },
    refreshPage() {
      this.refreshed = true;
    }
  };

  for (const [key, value] of Object.entries(pageConfig)) {
    if (typeof value === 'function' && key !== 'data') {
      page[key] = value;
    }
  }

  return { pageConfig, page, requestCalls, auth };
}

test('profile page exposes a register-mode switch for new users', () => {
  const { pageConfig } = loadPage({
    requestHandler() {
      throw new Error('not needed');
    }
  });

  assert.equal(typeof pageConfig.startRegisterFlow, 'function');
});

test('profile register mode submits nickname and phone to register endpoint', async () => {
  const { pageConfig, page, requestCalls } = loadPage({
    requestHandler(options) {
      options.success({
        statusCode: 200,
        data: {
          code: 'OK',
          data: { userId: 3 }
        }
      });
    }
  });

  page.data.authFlowMode = 'register';
  page.data.profileForm = {
    nickname: '小王',
    phoneNumber: '13800000000'
  };

  await pageConfig.submitProfile.call(page);

  assert.equal(requestCalls.length, 1);
  assert.match(requestCalls[0].url, /\/api\/auth\/register-phone$/);
  assert.equal(requestCalls[0].data.nickname, '小王');
});

test('wechat login opens name completion when it creates a placeholder customer', async () => {
  const { pageConfig, page } = loadPage({
    requestHandler(options) {
      if (options.url.endsWith('/api/auth/bind-phone')) {
        options.success({
          statusCode: 200,
          data: {
            code: 'OK',
            data: {
              token: 'token-1',
              userId: 9,
              userType: 'customer'
            }
          }
        });
        return;
      }

      if (options.url.endsWith('/api/mobile/customer/home')) {
        options.success({
          statusCode: 200,
          data: {
            code: 'OK',
            data: {
              name: '微信用户-0000',
              phone: '13800000000'
            }
          }
        });
      }
    }
  });

  await pageConfig.getPhoneNumber.call(page, {
    detail: {
      errMsg: 'getPhoneNumber:ok',
      code: 'code-1',
      phoneNumber: '13800000000'
    }
  });

  assert.equal(page.data.authFlowMode, 'complete-profile');
  assert.equal(page.data.showAuthPopup, true);
});
