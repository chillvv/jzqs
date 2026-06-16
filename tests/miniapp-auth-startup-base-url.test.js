const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const authJs = fs.readFileSync(path.join(repoRoot, 'miniapp/utils/auth.js'), 'utf8');

assert.match(
  authJs,
  /const \{ DEFAULT_API_BASE_URL, resolveApiBaseUrl \} = require\('\.\/api-base'\);/,
  'auth.js 应复用 api-base 配置，避免启动早期直接依赖 getApp().globalData'
);

assert.match(
  authJs,
  /const app = typeof getApp === 'function' \? getApp\(\) : null;/,
  'auth.js 请求方法应先安全获取 getApp()，避免启动期 getApp 不可用时报错'
);

assert.match(
  authJs,
  /const apiBaseUrl = app && app\.globalData\s*\?\s*resolveApiBaseUrl\(app\.globalData\.apiBaseUrl\)\s*:\s*resolveApiBaseUrl\(wx\.getStorageSync\('apiBaseUrl'\)\s*\|\|\s*DEFAULT_API_BASE_URL\);/,
  'auth.js 请求方法应在启动期回退到本地或默认 apiBaseUrl'
);

assert.doesNotMatch(
  authJs,
  /url:\s*getApp\(\)\.globalData\.apiBaseUrl \+ url/,
  'auth.js 请求方法不应继续直接访问 getApp().globalData.apiBaseUrl'
);

console.log('PASS: auth.js 启动期 apiBaseUrl 兜底检查通过');
