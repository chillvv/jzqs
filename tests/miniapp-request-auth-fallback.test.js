const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const requestJs = fs.readFileSync(path.join(repoRoot, 'miniapp/utils/request.js'), 'utf8');

assert.match(
  requestJs,
  /const resolveToken = \(\) => app\.globalData\.token \|\| wx\.getStorageSync\('auth_token'\);/,
  'request.js 应在全局 token 缺失时回退读取本地 auth_token，避免授权弹窗后丢失请求头'
);

console.log('PASS: request.js 认证兜底检查通过');
