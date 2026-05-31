const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const rootDir = path.resolve(__dirname, '..');
const profileWxml = fs.readFileSync(path.join(rootDir, 'pages', 'profile', 'index.wxml'), 'utf8');
const profileWxss = fs.readFileSync(path.join(rootDir, 'pages', 'profile', 'index.wxss'), 'utf8');

test('profile page uses a small guest login trigger instead of a large auth card', () => {
  assert.match(profileWxml, /去登录/);
  assert.match(profileWxml, /profile-guest-action/);
  assert.doesNotMatch(profileWxml, /guest-service-card__primary/);
  assert.doesNotMatch(profileWxml, /确认你的会员手机号/);
});

test('profile login popup keeps phone login first and wechat button secondary width', () => {
  const phoneIndex = profileWxml.indexOf('>手机号登录</button>');
  const wechatIndex = profileWxml.indexOf('微信一键登录');

  assert.notEqual(phoneIndex, -1);
  assert.notEqual(wechatIndex, -1);
  assert.ok(phoneIndex < wechatIndex);
  assert.match(profileWxml, /class="auth-submit-btn auth-entry-btn"/);
  assert.match(profileWxml, /class="btn-wechat-alt auth-entry-btn"/);
  assert.match(profileWxml, /<button[\s\S]*class="btn-wechat-alt auth-entry-btn"[\s\S]*>\s*微信一键登录\s*<\/button>/);
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
  assert.match(profileWxss, /\.btn-wechat-alt\s*\{[\s\S]*color:\s*#FFFFFF;/);
});
