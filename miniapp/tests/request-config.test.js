const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const requestModulePath = path.join(__dirname, '..', 'utils', 'request.js');

test('request uses service headers from app global config', async () => {
  const requestCalls = [];

  global.wx = {
    getStorageSync() {
      return 'token-123';
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
    }
  };

  global.getApp = () => ({
    globalData: {
      apiBaseUrl: 'http://localhost:8081',
      token: null,
      serviceHeaders: {
        'X-WX-SERVICE': 'customer-service',
        'X-Vm-Service': 'customer-vm'
      }
    },
    waitForAuth() {
      return Promise.resolve();
    },
    handleUnauthorized() {}
  });

  delete require.cache[require.resolve(requestModulePath)];
  const requestUtils = require(requestModulePath);

  await requestUtils.request({
    url: '/api/mobile/customer/home',
    requireAuth: false
  });

  assert.equal(requestCalls.length, 1);
  assert.equal(requestCalls[0].header['X-WX-SERVICE'], 'customer-service');
  assert.equal(requestCalls[0].header['X-Vm-Service'], 'customer-vm');
  assert.equal(requestCalls[0].header.Authorization, 'Bearer token-123');
});
