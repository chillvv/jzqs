const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const filesToCheck = [
  path.join(repoRoot, 'miniapp-rider', 'utils', 'auth.js'),
  path.join(repoRoot, 'miniapp-rider', 'app.js'),
  path.join(repoRoot, 'miniapp-rider', 'pages', 'test-login', 'index.js')
];

const forbiddenEndpoints = [
  '/api/auth/login',
  '/api/auth/verify',
  '/api/auth/bind-phone',
  '/api/rider/me'
];

for (const file of filesToCheck) {
  const content = fs.readFileSync(file, 'utf8');
  for (const endpoint of forbiddenEndpoints) {
    assert.equal(
      content.includes(endpoint),
      false,
      `${path.relative(repoRoot, file)} 仍引用会触发骑手端微信登录异常的旧接口: ${endpoint}`
    );
  }
}

console.log('PASS: 骑手小程序未再引用会触发 appid missing 的旧认证接口');
