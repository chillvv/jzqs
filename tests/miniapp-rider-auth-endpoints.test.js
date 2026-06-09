const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const filesToCheck = [
  path.join(repoRoot, 'miniapp-rider', 'utils', 'auth.js'),
  path.join(repoRoot, 'miniapp-rider', 'app.js'),
  path.join(repoRoot, 'miniapp-rider', 'pages', 'test-login', 'index.js'),
  path.join(repoRoot, 'miniapp-rider', 'services', 'auth.service.js'),
  path.join(repoRoot, 'miniapp-rider', 'pages', 'profile', 'index.js')
];

const forbiddenEndpoints = [
  '/api/auth/login',
  '/api/auth/verify',
  '/api/auth/bind-phone',
  '/api/rider/me'
];

const filesForbiddenPhoneFields = [
  path.join(repoRoot, 'miniapp-rider', 'pages', 'profile', 'index.js'),
  path.join(repoRoot, 'miniapp-rider', 'utils', 'rider-profile-auth.js')
].filter((file) => fs.existsSync(file));

const riderServiceFile = path.join(repoRoot, 'miniapp-rider', 'services', 'auth.service.js');
const riderAuthFile = path.join(repoRoot, 'miniapp-rider', 'utils', 'auth.js');
const riderProfileFile = path.join(repoRoot, 'miniapp-rider', 'pages', 'profile', 'index.js');

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

for (const file of filesForbiddenPhoneFields) {
  const content = fs.readFileSync(file, 'utf8');
  assert.equal(
    content.includes('detail.phoneNumber'),
    false,
    `${path.relative(repoRoot, file)} 仍在直接读取微信明文手机号`
  );
}

const riderServiceContent = fs.readFileSync(riderServiceFile, 'utf8');
for (const legacyField of ['encryptedData', 'iv']) {
  assert.equal(
    riderServiceContent.includes(legacyField),
    false,
    `miniapp-rider/services/auth.service.js 仍依赖旧字段: ${legacyField}`
  );
}

const riderAuthContent = fs.readFileSync(riderAuthFile, 'utf8');
const riderProfileContent = fs.readFileSync(riderProfileFile, 'utf8');
assert.equal(
  riderAuthContent.includes('当前环境暂不支持微信一键登录，请使用手机号登录'),
  false,
  'miniapp-rider/utils/auth.js 仍在前端阻止微信手机号登录'
);

assert.match(
  riderProfileContent,
  /async handleMenuClick\(e\)\s*\{[\s\S]*await app\.waitForRiderAuth\(\);[\s\S]*if \(!app\.globalData\.riderRegistered[\s\S]*this\.goLoginPage\(\);[\s\S]*\}/,
  'miniapp-rider/pages/profile/index.js 的菜单入口应等待骑手认证完成后再判断是否跳登录'
);

console.log('PASS: 骑手小程序已改为微信手机号 code -> 后端换号链路');
