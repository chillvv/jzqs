const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');

const files = {
  customerApp: path.join(repoRoot, 'miniapp', 'app.json'),
  riderApp: path.join(repoRoot, 'miniapp-rider', 'app.json'),
  customerProfile: path.join(repoRoot, 'miniapp', 'pages', 'profile', 'index.js'),
  riderProfile: path.join(repoRoot, 'miniapp-rider', 'pages', 'profile', 'index.js')
};

for (const [label, file] of Object.entries(files)) {
  assert.ok(fs.existsSync(file), `${label} missing: ${file}`);
}

const customerAppJson = fs.readFileSync(files.customerApp, 'utf8');
const riderAppJson = fs.readFileSync(files.riderApp, 'utf8');

assert.equal(
  customerAppJson.includes('__usePrivacyCheck__'),
  true,
  'miniapp/app.json 缺少 __usePrivacyCheck__ 隐私检查开关'
);

assert.equal(
  riderAppJson.includes('__usePrivacyCheck__'),
  true,
  'miniapp-rider/app.json 缺少 __usePrivacyCheck__ 隐私检查开关'
);

const customerProfile = fs.readFileSync(files.customerProfile, 'utf8');
const riderProfile = fs.readFileSync(files.riderProfile, 'utf8');

assert.equal(
  customerProfile.includes('ensurePhonePrivacyPermission'),
  true,
  'miniapp/pages/profile/index.js 未接入手机号隐私权限辅助逻辑'
);

assert.equal(
  riderProfile.includes('ensurePhonePrivacyPermission'),
  true,
  'miniapp-rider/pages/profile/index.js 未接入手机号隐私权限辅助逻辑'
);

assert.equal(
  customerProfile.includes('wx.request({') && customerProfile.includes('}).catch(() => {})'),
  false,
  'miniapp/pages/profile/index.js 仍对 wx.request 链接了无效的 .catch()'
);

assert.equal(
  riderProfile.includes('wx.request({') && riderProfile.includes('}).catch(() => {})'),
  false,
  'miniapp-rider/pages/profile/index.js 仍对 wx.request 链接了无效的 .catch()'
);

console.log('PASS: 两个小程序已接入手机号隐私权限前置');
