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

test('request uses service headers from app global config', async () => {
  const requestCalls = [];

  global.wx = {
    getStorageSync() {
      return 'token-456';
    },
    removeStorageSync() {},
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
      riderAuthReady: true,
      serviceHeaders: {
        'X-WX-SERVICE': 'rider-service',
        'X-Vm-Service': 'rider-vm'
      }
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

  await requestUtils.request({
    url: '/api/mobile/rider/tasks',
    requireWorkbench: false
  });

  assert.equal(requestCalls.length, 1);
  assert.equal(requestCalls[0].header['X-WX-SERVICE'], 'rider-service');
  assert.equal(requestCalls[0].header['X-Vm-Service'], 'rider-vm');
});

test('uploadFile only keeps token in authorization header', async () => {
  const uploadCalls = [];

  global.wx = {
    getStorageSync() {
      return 'token-456';
    },
    removeStorageSync() {},
    request() {},
    uploadFile(options) {
      uploadCalls.push(options);
      options.success({
        statusCode: 200,
        data: JSON.stringify({
          code: 'OK',
          data: { ok: true }
        })
      });
    }
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

  await requestUtils.uploadFile({
    url: '/api/mobile/rider/upload',
    filePath: '/tmp/file.png',
    formData: { scene: 'receipt' },
    requireWorkbench: false
  });

  assert.equal(uploadCalls.length, 1);
  assert.equal(uploadCalls[0].header.Authorization, 'Bearer token-456');
  assert.equal(uploadCalls[0].formData.scene, 'receipt');
  assert.equal('token' in uploadCalls[0].formData, false);
});
