const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const rootDir = path.resolve(__dirname, '..');
const profileWxml = fs.readFileSync(path.join(rootDir, 'pages', 'profile', 'index.wxml'), 'utf8');
const profileJs = fs.readFileSync(path.join(rootDir, 'pages', 'profile', 'index.js'), 'utf8');
const appJs = fs.readFileSync(path.join(rootDir, 'app.js'), 'utf8');

test('profile page stays minimal for rider login flow', () => {
  assert.match(profileWxml, /去登录/);
  assert.match(profileWxml, /骑手微信登录/);
  assert.match(profileWxml, /验证并登录/);
  assert.match(profileWxml, /请使用微信手机号登录已开通的骑手账号/);
  assert.doesNotMatch(profileWxml, /登录 \/ 注册/);
  assert.doesNotMatch(profileWxml, /请先登录/);
  assert.doesNotMatch(profileWxml, /点击下面按钮，先完成骑手登录/);
  assert.doesNotMatch(profileWxml, /登录后可用/);
  assert.doesNotMatch(profileWxml, /开始使用/);
  assert.doesNotMatch(profileWxml, /开发设置/);
  assert.doesNotMatch(profileWxml, /联系商家/);
  assert.doesNotMatch(profileWxml, /使用说明/);
  assert.doesNotMatch(profileWxml, /清理缓存/);
  assert.doesNotMatch(profileJs, /guestBenefits/);
  assert.doesNotMatch(profileJs, /guestSteps/);
  assert.doesNotMatch(profileJs, /copyApiBaseUrl/);
  assert.doesNotMatch(profileJs, /contactMerchant/);
  assert.doesNotMatch(profileJs, /showHelp/);
  assert.doesNotMatch(profileJs, /clearCache/);
  assert.match(appJs, /后台未开通该手机号对应的骑手账号/);
});
