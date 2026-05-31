const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const requestModulePath = path.join(__dirname, '..', 'utils', 'request.js');

function loadRequestWithMocks() {
  const requestCalls = [];
  const removedKeys = [];
  const storage = new Map([['auth_token', 'token-456']]);

  global.wx = {
    getStorageSync(key) {
      return storage.get(key);
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
          data: { ok: true }
        }
      });
    },
    uploadFile() {}
  };

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

  delete require.cache[require.resolve(requestModulePath)];
  const requestUtils = require(requestModulePath);

  return { requestUtils, requestCalls, removedKeys };
}

test('request uses auth_token as bearer token header for rider requests', async () => {
  const { requestUtils, requestCalls } = loadRequestWithMocks();

  await requestUtils.request({
    url: '/api/mobile/rider/tasks',
    requireWorkbench: false
  });

  assert.equal(requestCalls.length, 1);
  assert.equal(requestCalls[0].header.Authorization, 'Bearer token-456');
  assert.equal('X-Custom-Token' in requestCalls[0].header, false);
});

test('request clears auth_token when backend returns 401', async () => {
  const { requestUtils, removedKeys } = loadRequestWithMocks();

  global.wx.request = (options) => {
    options.success({
      statusCode: 401,
      data: {
        code: 'UNAUTHORIZED',
        message: 'expired'
      }
    });
  };

  await assert.rejects(
    requestUtils.request({
      url: '/api/mobile/rider/tasks',
      requireWorkbench: false
    }),
    /登录已过期/
  );

  assert.deepEqual(removedKeys, ['auth_token']);
});
