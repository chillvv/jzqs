const test = require('node:test');
const assert = require('node:assert/strict');

const cloud = require('./index.js');

test('readRequiredConfig rejects placeholder token', () => {
  assert.throws(
    () => cloud.__test__.readRequiredConfig({
      API_BASE_URL: 'https://jzqs.top',
      INTERNAL_API_TOKEN: 'change_this_to_an_internal_call_secret'
    }),
    /INTERNAL_API_TOKEN/
  );
});

test('requestJson rejects non-2xx response', async () => {
  const fakeHttps = {
    request(options, handler) {
      const listeners = {};
      const res = {
        statusCode: 500,
        on(event, cb) {
          listeners[event] = cb;
        }
      };
      handler(res);
      process.nextTick(() => {
        listeners.data?.('boom');
        listeners.end?.();
      });
      return {
        on() {},
        write() {},
        end() {},
        destroy(err) {
          throw err;
        }
      };
    }
  };

  await assert.rejects(
    cloud.__test__.requestJson(
      fakeHttps,
      'GET',
      'https://jzqs.top/api/internal/maintenance/cloud-job-logs',
      'token'
    ),
    /500/
  );
});
