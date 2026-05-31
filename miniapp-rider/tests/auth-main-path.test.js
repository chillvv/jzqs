const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const authServiceModulePath = path.join(__dirname, '..', 'services', 'auth.service.js');
const authModulePath = path.join(__dirname, '..', 'utils', 'auth.js');

function createWxMock() {
  const requestCalls = [];
  const storage = new Map();

  global.wx = {
    getStorageSync(key) {
      return storage.get(key);
    },
    setStorageSync(key, value) {
      storage.set(key, value);
    },
    removeStorageSync(key) {
      storage.delete(key);
    },
    login() {
      return Promise.resolve({ code: 'wx-login-code' });
    },
    request(options) {
      requestCalls.push(options);
      options.success({
        statusCode: 200,
        data: {
          code: 'OK',
          data: {
            token: 'token-rider',
            userId: 9,
            userType: 'rider',
            phone: '138****0009',
            riderName: '骑手小李',
            riderStatus: 'ACTIVE',
            workbenchEnabled: true
          }
        }
      });
    }
  };

  global.getApp = () => ({
    globalData: {
      apiBaseUrl: 'http://localhost:8081'
    }
  });

  return { requestCalls, storage };
}

test('auth service defaults rider register to unified /api/auth/register-phone', async () => {
  const { requestCalls } = createWxMock();
  global.getApp = () => ({
    globalData: {
      apiBaseUrl: 'http://localhost:8081',
      riderAuthReady: true
    },
    canUseWorkbench() {
      return true;
    },
    waitForRiderAuth() {
      return Promise.resolve();
    },
    getWorkbenchBlockMessage() {
      return 'blocked';
    },
    resetRiderAuthState() {}
  });

  delete require.cache[require.resolve(authServiceModulePath)];
  const authService = require(authServiceModulePath);

  await authService.riderRegister('13800000009', '骑手小李', 'openid-1');

  assert.equal(requestCalls.length, 1);
  assert.equal(requestCalls[0].url, 'http://localhost:8081/api/auth/register-phone');
  assert.deepEqual(requestCalls[0].data, {
    phone: '13800000009',
    nickname: '骑手小李',
    openid: 'openid-1',
    userType: 'rider'
  });
});

test('auth.phoneLogin delegates to unified rider phone login and updates auth state', async () => {
  const { requestCalls } = createWxMock();

  delete require.cache[require.resolve(authModulePath)];
  const auth = require(authModulePath);

  await auth.phoneLogin('13800000009', 'openid-1');

  assert.equal(requestCalls.length, 1);
  assert.equal(requestCalls[0].url, 'http://localhost:8081/api/auth/phone-login');
  assert.deepEqual(requestCalls[0].data, {
    phone: '13800000009',
    openid: 'openid-1',
    userType: 'rider'
  });
  assert.equal(auth.globalData.loggedIn, true);
  assert.equal(auth.globalData.riderName, '骑手小李');
  assert.equal(auth.globalData.riderStatus, 'ACTIVE');
  assert.equal(auth.globalData.workbenchEnabled, true);
});
