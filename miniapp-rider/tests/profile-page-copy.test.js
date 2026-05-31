const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const profileWxml = fs.readFileSync(path.join(__dirname, '..', 'pages', 'profile', 'index.wxml'), 'utf8');
const profileWxss = fs.readFileSync(path.join(__dirname, '..', 'pages', 'profile', 'index.wxss'), 'utf8');

test('rider profile stays professional and icon-free', () => {
  assert.match(profileWxml, /历史订单/);
  assert.match(profileWxml, /账号设置/);
  assert.doesNotMatch(profileWxml, /📄/);
  assert.doesNotMatch(profileWxml, /⚙️/);
  assert.doesNotMatch(profileWxml, /🚪/);
});

test('rider profile login popup keeps phone login first and wechat button secondary width', () => {
  const phoneIndex = profileWxml.indexOf('>验证并登录</button>');
  const wechatIndex = profileWxml.indexOf('骑手微信登录');

  assert.notEqual(phoneIndex, -1);
  assert.notEqual(wechatIndex, -1);
  assert.ok(phoneIndex < wechatIndex);
  assert.match(profileWxml, /class="auth-submit-btn auth-entry-btn"/);
  assert.match(profileWxml, /class="btn-wechat-alt auth-entry-btn"/);
  assert.match(profileWxml, /<button[\s\S]*class="btn-wechat-alt auth-entry-btn"[\s\S]*>\s*骑手微信登录\s*<\/button>/);
  assert.match(profileWxss, /padding-bottom:\s*calc\(env\(safe-area-inset-bottom\)\s*\+\s*160rpx\)/);
  assert.match(profileWxss, /\.auth-entry-btn\s*\{[\s\S]*width:\s*320rpx(?:\s*!important)?;/);
  assert.match(profileWxss, /\.auth-entry-btn\s*\{[\s\S]*height:\s*88rpx;/);
  assert.match(profileWxss, /\.auth-entry-btn\s*\{[\s\S]*display:\s*flex\s*!important;/);
  assert.match(profileWxss, /\.auth-entry-btn\s*\{[\s\S]*align-items:\s*center\s*!important;/);
  assert.match(profileWxss, /\.auth-entry-btn\s*\{[\s\S]*justify-content:\s*center\s*!important;/);
  assert.match(profileWxss, /\.auth-entry-btn\s*\{[\s\S]*padding:\s*0\s*!important;/);
  assert.doesNotMatch(profileWxss, /\.btn-wechat-alt\s*\{[\s\S]*width:\s*auto\s*!important;/);
  assert.doesNotMatch(profileWxss, /\.btn-wechat-alt\s*\{[\s\S]*min-width:\s*\d+rpx/);
  assert.match(profileWxss, /\.btn-wechat-alt\s*\{[\s\S]*background:\s*#07C160;/);
});
