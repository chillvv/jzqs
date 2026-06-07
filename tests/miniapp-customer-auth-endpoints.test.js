const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const filesToCheck = [
  path.join(repoRoot, 'miniapp', 'utils', 'auth.js'),
  path.join(repoRoot, 'miniapp', 'pages', 'profile', 'index.js'),
  path.join(repoRoot, 'miniapp', 'pages', 'register', 'index.js'),
  path.join(repoRoot, 'miniapp', 'pages', 'auth-demo', 'index.js')
];

const forbiddenEndpoints = [
  '/api/auth/login',
  '/api/auth/customer-phone-login',
  '/api/auth/register-phone',
  '/api/auth/phone-login',
  '/api/auth/verify',
  '/api/auth/logout'
];

const requiredEndpointChecks = [
  {
    file: path.join(repoRoot, 'miniapp', 'utils', 'auth.js'),
    endpoint: '/api/mobile/auth/bind-phone'
  }
];

const forbiddenPhoneFieldFiles = [
  path.join(repoRoot, 'miniapp', 'pages', 'profile', 'index.js'),
  path.join(repoRoot, 'miniapp', 'pages', 'auth-demo', 'index.js'),
  path.join(repoRoot, 'miniapp', 'utils', 'profile-auth.js')
];

for (const file of filesToCheck) {
  const content = fs.readFileSync(file, 'utf8');
  for (const endpoint of forbiddenEndpoints) {
    assert.equal(
      content.includes(endpoint),
      false,
      `${path.relative(repoRoot, file)} 仍引用旧顾客认证接口: ${endpoint}`
    );
  }
}

for (const { file, endpoint } of requiredEndpointChecks) {
  const content = fs.readFileSync(file, 'utf8');
  assert.equal(
    content.includes(endpoint),
    true,
    `${path.relative(repoRoot, file)} 未接入微信手机号动态令牌接口: ${endpoint}`
  );
}

for (const file of forbiddenPhoneFieldFiles) {
  const content = fs.readFileSync(file, 'utf8');
  assert.equal(
    content.includes('detail.phoneNumber'),
    false,
    `${path.relative(repoRoot, file)} 仍在直接读取微信明文手机号`
  );
}

console.log('PASS: 顾客小程序已改为微信手机号 code -> 后端换号链路');
