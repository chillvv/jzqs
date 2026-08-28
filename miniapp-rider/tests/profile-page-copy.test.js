const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const profileWxml = fs.readFileSync(path.join(__dirname, '..', 'pages', 'profile', 'index.wxml'), 'utf8');
const profileWxss = fs.readFileSync(path.join(__dirname, '..', 'pages', 'profile', 'index.wxss'), 'utf8');

test('rider profile shows brand header and core menu entries', () => {
  assert.match(profileWxml, /简知轻食/);
  assert.match(profileWxml, /历史订单/);
  assert.match(profileWxml, /账号设置/);
  assert.match(profileWxml, /新手指引/);
  assert.match(profileWxml, /退出登录/);
  assert.match(profileWxml, /今日完成单量/);
});

test('rider profile keeps minimal entries without extra login-promo or guest flow blocks', () => {
  assert.doesNotMatch(profileWxml, /登录 \/ 注册/);
  assert.doesNotMatch(profileWxml, /请先登录/);
  assert.doesNotMatch(profileWxml, /开始使用/);
  assert.doesNotMatch(profileWxml, /开发设置/);
  assert.doesNotMatch(profileWxml, /联系商家/);
  assert.doesNotMatch(profileWxml, /使用说明/);
  assert.doesNotMatch(profileWxml, /清理缓存/);
  assert.match(profileWxml, /去登录/);
});

test('rider profile page uses consistent logout button style', () => {
  assert.match(profileWxml, /class="btn-logout"[^>]*bindtap="logout"/);
  assert.match(profileWxss, /\.btn-logout\s*\{/);
  assert.match(profileWxss, /\.btn-logout::after\s*\{[\s\S]*border:\s*none/);
});
