const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const rootDir = path.resolve(__dirname, '..');
const profileWxml = fs.readFileSync(path.join(rootDir, 'pages', 'profile', 'index.wxml'), 'utf8');
const profileWxss = fs.readFileSync(path.join(rootDir, 'pages', 'profile', 'index.wxss'), 'utf8');
const loginWxml = fs.readFileSync(path.join(rootDir, 'pages', 'login', 'index.wxml'), 'utf8');
const orderWxml = fs.readFileSync(path.join(rootDir, 'pages', 'order', 'index.wxml'), 'utf8');

test('profile page uses a small guest login trigger instead of a large auth card', () => {
  assert.match(profileWxml, /去登录/);
  assert.match(profileWxml, /profile-guest-action/);
  assert.doesNotMatch(profileWxml, /guest-service-card__primary/);
  assert.doesNotMatch(profileWxml, /确认你的会员手机号/);
});

test('login entry points use neutral phone login copy without WeChat branding', () => {
  assert.match(loginWxml, /手机号快捷登录/);
  assert.doesNotMatch(loginWxml, /微信一键/);
  assert.doesNotMatch(loginWxml, /微信登录/);
  assert.match(orderWxml, /手机号快捷登录/);
  assert.doesNotMatch(orderWxml, /微信一键登录/);
  assert.doesNotMatch(profileWxml, /微信一键登录/);
});
