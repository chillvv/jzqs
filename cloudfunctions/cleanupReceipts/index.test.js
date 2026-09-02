const { describe, test } = require('node:test');
const assert = require('node:assert/strict');

const CLOUD_ID = 'wx-server-sdk';

function installCloudMock(overrides = {}) {
  const cloud = {
    DYNAMIC_CURRENT_ENV: Symbol('env'),
    init: () => {},
    deleteFile: overrides.deleteFile || (async () => ({ fileList: [] }))
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

function withMainEnv(env = {}) {
  const previous = { ...process.env };
  process.env.API_BASE_URL = env.API_BASE_URL || 'https://jzqs.top';
  process.env.INTERNAL_API_TOKEN = env.INTERNAL_API_TOKEN || 'secret-token-123';
  return () => {
    Object.keys(previous).forEach((key) => delete process.env[key]);
    Object.assign(process.env, previous);
  };
}

// 所有测试共享 require.cache mock（wx-server-sdk / https），Node 顶层 test 默认并发会交叉污染，
// 因此整个文件必须在一个串行 describe 中执行。
describe('cleanupReceipts 云函数', { concurrency: false }, () => {
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

  test('readRequiredConfig rejects missing internal token', () => {
    const { __test__ } = freshIndex();
    assert.throws(
      () => __test__.readRequiredConfig({ API_BASE_URL: 'https://jzqs.top' }),
      /INTERNAL_API_TOKEN 未配置/
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

  test('requestJson sends X-Internal-Token and parses JSON', async () => {
    let captured = null;
    installHttpsMock((opts, callback) => {
      captured = opts;
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'data') fn(JSON.stringify({ data: { fileIds: ['cloud://a.jpg'], cutoff: '今天 00:00' } })); if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { __test__ } = freshIndex();
    const result = await __test__.requestJson(require('https'), 'GET', 'https://jzqs.top/api/internal/receipts/expired-file-ids', 'token-abc');
    assert.equal(captured.headers['X-Internal-Token'], 'token-abc');
    assert.equal(result.data.fileIds.length, 1);
  });

  test('requestJson sends Content-Type and Content-Length on POST', async () => {
    let captured = null;
    installHttpsMock((opts, callback) => {
      captured = opts;
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { __test__ } = freshIndex();
    await __test__.requestJson(require('https'), 'POST', 'https://jzqs.top/notify', 'token', { fileIds: ['cloud://a.jpg'] });
    assert.equal(captured.method, 'POST');
    assert.equal(captured.headers['Content-Type'], 'application/json');
    assert.equal(Number(captured.headers['Content-Length']), Buffer.byteLength(JSON.stringify({ fileIds: ['cloud://a.jpg'] })));
  });

  test('requestJson rejects non-2xx response', async () => {
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 401, on: (evt, fn) => { if (evt === 'data') fn('unauthorized'); if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { __test__ } = freshIndex();
    await assert.rejects(
      () => __test__.requestJson(require('https'), 'GET', 'https://jzqs.top/x', 'token'),
      /请求失败: 401/
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

  test('drainExpiredFileIds stops immediately when first batch is empty', async () => {
    const { __test__ } = freshIndex();
    let processorCalls = 0;
    await __test__.drainExpiredFileIds(
      async () => ({ fileIds: [], cutoff: '今天 00:00' }),
      async () => { processorCalls++; },
      { fileIds: [], cutoff: '今天 00:00' }
    );
    assert.equal(processorCalls, 0);
  });

  test('drainExpiredFileIds processes a single small batch once', async () => {
    const { __test__ } = freshIndex();
    const processed = [];
    await __test__.drainExpiredFileIds(
      async () => ({ fileIds: [], cutoff: '今天 00:00' }),
      async (batch, round) => { processed.push({ ids: batch.fileIds, round }); },
      { fileIds: ['cloud://a.jpg'], cutoff: '今天 00:00' }
    );
    assert.equal(processed.length, 1);
    assert.equal(processed[0].ids.length, 1);
    assert.equal(processed[0].round, 1);
  });

  test('drainExpiredFileIds keeps pulling while batches are full (>=500)', async () => {
    const { __test__ } = freshIndex();
    let fetchCount = 0;
    const full = Array.from({ length: 500 }, (_, i) => `cloud://f${i}.jpg`);
    const processed = [];
    await __test__.drainExpiredFileIds(
      async () => {
        fetchCount++;
        // firstBatch 直接作为第一轮；此后每轮调用 fetcher：
        // 第一次拉取（fetchCount=1）返回满 500，第二次（fetchCount=2）返回空退出
        return fetchCount === 1 ? { fileIds: full, cutoff: '今天 00:00' } : { fileIds: [], cutoff: '今天 00:00' };
      },
      async (batch, round) => { processed.push({ count: batch.fileIds.length, round }); },
      { fileIds: full, cutoff: '今天 00:00' }
    );
    // 第一轮用 firstBatch（500），第二轮用拉取的满 500，第三轮拉取到空退出
    assert.equal(processed.length, 2);
    assert.equal(processed[0].count, 500);
    assert.equal(processed[1].count, 500);
    assert.equal(processed[0].round, 1);
    assert.equal(processed[1].round, 2);
    assert.equal(fetchCount, 2);
  });

  test('drainExpiredFileIds caps at 20 rounds', async () => {
    const { __test__ } = freshIndex();
    const full = Array.from({ length: 500 }, (_, i) => `cloud://f${i}.jpg`);
    let rounds = 0;
    await __test__.drainExpiredFileIds(
      async () => ({ fileIds: full, cutoff: '今天 00:00' }),
      async () => { rounds++; },
      { fileIds: full, cutoff: '今天 00:00' }
    );
    assert.equal(rounds, 20);
  });

  test('main returns early with zero counts when no file ids are returned', async () => {
    installCloudMock({ deleteFile: async () => ({ fileList: [] }) });
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'data') fn(JSON.stringify({ data: { fileIds: [], cutoff: '今天 00:00' } })); if (evt === 'end') fn(); } };
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

  test('main deletes returned file ids in batches of 50', async () => {
    const deletedLists = [];
    installCloudMock({
      deleteFile: async ({ fileList }) => {
        deletedLists.push(fileList);
        return { fileList: fileList.map((fileID) => ({ fileID, status: 0 })) };
      }
    });
    const fileIds = Array.from({ length: 60 }, (_, i) => `cloud://img-${i}.jpg`);
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'data') fn(JSON.stringify({ data: { fileIds, cutoff: '今天 00:00' } })); if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { main } = freshIndex();
    const restore = withMainEnv();
    try {
      const result = await main({}, {});
      assert.equal(result.scanned, 60);
      assert.equal(result.deleted, 60);
      assert.equal(result.failed, 0);
      // 60 个文件按 50/10 分两批
      assert.equal(deletedLists.length, 2);
      assert.equal(deletedLists[0].length, 50);
      assert.equal(deletedLists[1].length, 10);
    } finally {
      restore();
    }
  });

  test('main counts per-file failures when status is non-zero', async () => {
    installCloudMock({
      deleteFile: async () => ({ fileList: [{ fileID: 'cloud://a.jpg', status: -1, errMsg: 'not found' }] })
    });
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'data') fn(JSON.stringify({ data: { fileIds: ['cloud://a.jpg'], cutoff: '今天 00:00' } })); if (evt === 'end') fn(); } };
        callback(res);
      });
      return { on: () => {}, write: () => {}, end: () => {} };
    });
    const { main } = freshIndex();
    const restore = withMainEnv();
    try {
      const result = await main({}, {});
      assert.equal(result.deleted, 0);
      assert.equal(result.failed, 1);
    } finally {
      restore();
    }
  });

  test('main counts batch-level failures when deleteFile throws', async () => {
    installCloudMock({
      deleteFile: async () => { throw new Error('batch boom'); }
    });
    installHttpsMock((opts, callback) => {
      setImmediate(() => {
        const res = { statusCode: 200, on: (evt, fn) => { if (evt === 'data') fn(JSON.stringify({ data: { fileIds: ['cloud://a.jpg'], cutoff: '今天 00:00' } })); if (evt === 'end') fn(); } };
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
      assert.ok(result.errors.some((msg) => msg.includes('batch boom')));
    } finally {
      restore();
    }
  });
});
