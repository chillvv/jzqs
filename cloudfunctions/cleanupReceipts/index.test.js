const test = require('node:test');
const assert = require('node:assert/strict');

const cloud = require('./index.js');

test('readRequiredConfig rejects non-https api base url', () => {
  assert.throws(
    () => cloud.__test__.readRequiredConfig({
      API_BASE_URL: 'http://jzqs.top',
      INTERNAL_API_TOKEN: 'very-strong-token-value'
    }),
    /API_BASE_URL/
  );
});

test('requestJson parses successful json response', async () => {
  const fakeHttps = {
    request(options, handler) {
      const listeners = {};
      const res = {
        statusCode: 200,
        on(event, cb) {
          listeners[event] = cb;
        }
      };
      handler(res);
      process.nextTick(() => {
        listeners.data?.('{"data":{"fileIds":["cloud://a"],"cutoff":"2026-06-23T00:00:00Z"}}');
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

  const result = await cloud.__test__.requestJson(
    fakeHttps,
    'GET',
    'https://jzqs.top/api/internal/receipts/expired-file-ids',
    'token'
  );

  assert.deepEqual(result, {
    data: {
      fileIds: ['cloud://a'],
      cutoff: '2026-06-23T00:00:00Z'
    }
  });
});

test('drainExpiredFileIds keeps pulling batches until backend returns empty', async () => {
  let calls = 0;
  const processed = [];
  const batches = [
    { fileIds: Array.from({ length: 500 }, (_, index) => `cloud://first-${index}`), cutoff: '2026-06-23T00:00:00Z' },
    { fileIds: ['cloud://tail-1', 'cloud://tail-2'], cutoff: '2026-06-23T00:00:00Z' },
    { fileIds: [], cutoff: '2026-06-23T00:00:00Z' }
  ];

  await cloud.__test__.drainExpiredFileIds(
    async () => {
      const batch = batches[calls];
      calls += 1;
      return batch;
    },
    async (batch, round) => {
      processed.push({ round, size: batch.fileIds.length });
    }
  );

  assert.equal(calls, 2);
  assert.deepEqual(processed, [
    { round: 1, size: 500 },
    { round: 2, size: 2 }
  ]);
});
