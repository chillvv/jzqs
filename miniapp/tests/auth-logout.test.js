const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const authModulePath = path.join(__dirname, '..', 'utils', 'auth.js');

function loadAuthWithMocks() {
  const requestCalls = [];
  const removedKeys = [];
  const storage = new Map([['auth_token', 'token-123'], ['auth_state', 'state-1']]);

  global.wx = {
    getStorageSync(key) {
      return storage.get(key);
    },
    setStorageSync(key, value) {
      storage.set(key, value);
    },
    removeStorageSync(key) {
      removedKeys.push(key);
      storage.delete(key);
    },
    request(options) {
      requestCalls.push(options);
      options.success({
        statusCode: 200,
        data: {
          code: 'OK',
          data: { message: '退出登录成功' }
        }
      });
    },
    redirectTo() {},
    login() {
      return Promise.resolve({ code: 'wx-code' });
    }
  };

  global.getApp = () => ({
    globalData: {
      apiBaseUrl: 'http://localhost:8081'
    }
  });

  delete require.cache[require.resolve(authModulePath)];
  const auth = require(authModulePath);

  auth.globalData.token = 'token-123';
  auth.globalData.userId = 7;
  auth.globalData.loggedIn = true;
  auth.globalData.registered = true;
  auth.globalData.ready = true;

  return { auth, requestCalls, removedKeys };
}

test('logout sends backend request with previous bearer token before clearing local state', async () => {
  const { auth, requestCalls, removedKeys } = loadAuthWithMocks();

  await auth.logout();

  assert.equal(requestCalls.length, 1);
  assert.equal(requestCalls[0].url, 'http://localhost:8081/api/auth/logout');
  assert.equal(requestCalls[0].method, 'POST');
  assert.equal(requestCalls[0].header.Authorization, 'Bearer token-123');
  assert.deepEqual(removedKeys, ['auth_token', 'auth_state']);
  assert.equal(auth.globalData.token, null);
});
