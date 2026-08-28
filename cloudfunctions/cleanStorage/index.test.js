const { describe, test } = require('node:test');
const assert = require('node:assert/strict');

const CLOUD_ID = 'wx-server-sdk';

function installCloudMock(overrides = {}) {
  const cloud = {
    DYNAMIC_CURRENT_ENV: Symbol('env'),
    init: () => {},
    deleteFile: overrides.deleteFile || (async () => ({ fileList: [] })),
    database: overrides.database || (() => {
      throw new Error('database not mocked');
    })
  };
  require.cache[require.resolve(CLOUD_ID)] = {
    id: CLOUD_ID,
    filename: CLOUD_ID,
    loaded: true,
    exports: cloud
  };
  return cloud;
}

function installHttpsMock(handler) {
  const fake = {
    request(opts, callback) {
      return handler(opts, callback);
    }
  };
  require.cache[require.resolve('https')] = {
    id: 'https',
    filename: 'https',
    loaded: true,
    exports: fake
  };
  return fake;
}

function freshIndex() {
  delete require.cache[require.resolve('./index.js')];
  return require('./index.js');
}

function withMainEnv() {
  const previous = { ...process.env };
  process.env.API_BASE_URL = 'https://jzqs.top';
  process.env.INTERNAL_API_TOKEN = 'secret-token-123';
  return () => {
    Object.keys(previous).forEach((key) => delete process.env[key]);
    Object.assign(process.env, previous);
  };
}

// 所有测试共享 require.cache mock，必须整体串行执行（Node 顶层 test 默认并发）
describe('cleanStorage 云函数', { concurrency: false }, () => {
  test('readRequiredConfig accepts valid https config and strips trailing slashes', () => {
    const { __test__ } = freshIndex();
    const config = __test__.readRequiredConfig({
      API_BASE_URL: 'https://jzqs.top/',
      INTERNAL_API_TOKEN: 'secret-token-123'
    });
    assert.equal(config.apiBaseUrl, 'https://jzqs.top');
    assert.equal(config.internalApiToken, 'secret-token-123');
  });

  test('readRequiredConfig rejects non-https API_BASE_URL', () => {
    const { __test__ } = freshIndex();
    assert.throws(
      () => __test__.readRequiredConfig({ API_BASE_URL: 'http://jzqs.top', INTERNAL_API_TOKEN: 't' }),
      /API_BASE_URL 未配置或不是合法的 https 地址/
    );
  });

  test('readRequiredConfig rejects missing API_BASE_URL', () => {
    const { __test__ } = freshIndex();
    assert.throws(
      () => __test__.readRequiredConfig({ INTERNAL_API_TOKEN: 't' }),
      /API_BASE_URL 未配置/
    );
  });

  test('readRequiredConfig rejects placeholder internal token', () => {
    const { __test__ } = freshIndex();
    assert.throws(
      () => __test__.readRequiredConfig({ API_BASE_URL: 'https://jzqs.top', INTERNAL_API_TOKEN: 'change_this_to_an_internal_call_secret' }),
      /INTERNAL_API_TOKEN 未配置或仍为占位值/
    );
  });

  test('requestJson sends X-Internal-Token header and parses JSON response', async () => {
    let captured = null;
    installHttpsMock((opts, callback) => {
      captured = opts;
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'data') fn(JSON.stringify({ ok: true })); if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { __test__ } = freshIndex();
    const result = await __test__.requestJson(require('https'), 'GET', 'https://jzqs.top/api/internal/receipts/expired-file-ids', 'token-abc');
    assert.equal(captured.headers['X-Internal-Token'], 'token-abc');
    assert.equal(captured.method, 'GET');
    assert.deepEqual(result, { ok: true });
  });

  test('requestJson rejects non-2xx response with status code', async () => {
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 500, on: (evt, fn) => { if (evt === 'data') fn('internal error'); if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { __test__ } = freshIndex();
    await assert.rejects(
      () => __test__.requestJson(require('https'), 'GET', 'https://jzqs.top/x', 'token'),
      /请求失败: 500/
    );
  });

  test('requestJson resolves null on empty body', async () => {
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { __test__ } = freshIndex();
    const result = await __test__.requestJson(require('https'), 'POST', 'https://jzqs.top/y', 'token');
    assert.equal(result, null);
  });

  test('requestJson sends Content-Type and Content-Length for POST with body', async () => {
    const acc = { captured: null, written: '' };
    installHttpsMock((opts, callback) => {
      acc.captured = opts;
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: (data) => { acc.written += data; }, end: () => {} };
    });
    const { __test__ } = freshIndex();
    await __test__.requestJson(require('https'), 'POST', 'https://jzqs.top/notify', 'token', { fileIds: ['cloud://a.jpg'] });
    assert.equal(acc.captured.method, 'POST');
    assert.equal(acc.captured.headers['Content-Type'], 'application/json');
    assert.equal(Number(acc.captured.headers['Content-Length']), Buffer.byteLength(JSON.stringify({ fileIds: ['cloud://a.jpg'] })));
    assert.ok(acc.written.includes('cloud://a.jpg'));
  });

  test('requestJson rejects when response body is not valid JSON', async () => {
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'data') fn('not-json{'); if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { __test__ } = freshIndex();
    await assert.rejects(
      () => __test__.requestJson(require('https'), 'GET', 'https://jzqs.top/x', 'token'),
      /解析响应失败/
    );
  });

  test('requestJson rejects on request error', async () => {
    installHttpsMock(() => {
      throw new Error('network down');
    });
    const { __test__ } = freshIndex();
    await assert.rejects(
      () => __test__.requestJson(require('https'), 'GET', 'https://jzqs.top/x', 'token'),
      /network down/
    );
  });

  test('main exits cleanly when no records need cleanup', async () => {
    const cloud = installCloudMock({
      deleteFile: async () => ({ fileList: [] }),
      database: () => ({
        command: { lt: (v) => v },
        collection: () => ({
          where: () => ({
            limit: () => ({ get: async () => ({ data: [] }) })
          }),
          doc: () => ({ remove: async () => ({}) })
        })
      })
    });
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { main } = freshIndex();
    const restore = withMainEnv();
    try {
      const result = await main({}, {});
      assert.equal(result.scanned, 0);
      assert.equal(result.deleted, 0);
      assert.equal(result.failed, 0);
    } finally {
      restore();
    }
  });

  test('main deletes cloud files and removes db records', async () => {
    const deletedFileLists = [];
    const removedDocs = [];
    const cloud = installCloudMock({
      deleteFile: async ({ fileList }) => {
        deletedFileLists.push(fileList);
        return { fileList: fileList.map((fileID) => ({ fileID, status: 0 })) };
      },
      database: () => ({
        command: { lt: (v) => v },
        collection: () => ({
          where: () => ({
            limit: () => ({ get: async () => ({ data: [
              { _id: 'rec-1', fileID: 'cloud://a.jpg' },
              { _id: 'rec-2', fileID: 'cloud://b.jpg' }
            ] }) })
          }),
          doc: (docId) => ({ remove: async () => { removedDocs.push(docId); return {}; } })
        })
      })
    });
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { main } = freshIndex();
    const restore = withMainEnv();
    try {
      const result = await main({}, {});
      assert.equal(result.scanned, 2);
      assert.equal(result.deleted, 2);
      assert.equal(result.failed, 0);
      assert.deepEqual(deletedFileLists, [['cloud://a.jpg', 'cloud://b.jpg']]);
      assert.deepEqual(removedDocs.sort(), ['rec-1', 'rec-2']);
    } finally {
      restore();
    }
  });

  test('main counts batch-level failures when deleteFile throws', async () => {
    const cloud = installCloudMock({
      deleteFile: async () => { throw new Error('batch boom'); },
      database: () => ({
        command: { lt: (v) => v },
        collection: () => ({
          where: () => ({
            limit: () => ({ get: async () => ({ data: [
              { _id: 'rec-1', fileID: 'cloud://a.jpg' }
            ] }) })
          }),
          doc: () => ({ remove: async () => ({}) })
        })
      })
    });
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { main } = freshIndex();
    const restore = withMainEnv();
    try {
      const result = await main({}, {});
      assert.equal(result.scanned, 1);
      assert.equal(result.deleted, 0);
      assert.equal(result.failed, 1);
    } finally {
      restore();
    }
  });

  test('main counts per-file failures when status is non-zero', async () => {
    const cloud = installCloudMock({
      deleteFile: async () => ({ fileList: [{ fileID: 'cloud://a.jpg', status: -1, errMsg: 'not found' }] }),
      database: () => ({
        command: { lt: (v) => v },
        collection: () => ({
          where: () => ({
            limit: () => ({ get: async () => ({ data: [
              { _id: 'rec-1', fileID: 'cloud://a.jpg' }
            ] }) })
          }),
          doc: () => ({ remove: async () => ({}) })
        })
      })
    });
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { main } = freshIndex();
    const restore = withMainEnv();
    try {
      const result = await main({}, {});
      assert.equal(result.scanned, 1);
      assert.equal(result.deleted, 0);
      assert.equal(result.failed, 1);
    } finally {
      restore();
    }
  });
});
