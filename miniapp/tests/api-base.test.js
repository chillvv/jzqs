const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const apiBaseModulePath = path.join(__dirname, '..', 'utils', 'api-base.js');

test('resolveApiBaseUrl falls back to stable local default when storage is empty', () => {
  delete require.cache[require.resolve(apiBaseModulePath)];
  const { DEFAULT_API_BASE_URL, resolveApiBaseUrl } = require(apiBaseModulePath);

  assert.equal(DEFAULT_API_BASE_URL, 'http://127.0.0.1:8081');
  assert.equal(resolveApiBaseUrl(''), 'http://127.0.0.1:8081');
  assert.equal(resolveApiBaseUrl('   '), 'http://127.0.0.1:8081');
});

test('resolveApiBaseUrl trims and removes trailing slash from storage override', () => {
  delete require.cache[require.resolve(apiBaseModulePath)];
  const { resolveApiBaseUrl } = require(apiBaseModulePath);

  assert.equal(resolveApiBaseUrl(' http://192.168.10.24:8081/ '), 'http://192.168.10.24:8081');
});
