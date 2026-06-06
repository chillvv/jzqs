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
  '/api/auth/bind-phone',
  '/api/auth/customer-phone-login',
  '/api/auth/register-phone',
  '/api/auth/phone-login',
  '/api/auth/verify',
  '/api/auth/logout'
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

console.log('PASS: 顾客小程序未再引用旧 /api/auth/* 顾客认证接口');
