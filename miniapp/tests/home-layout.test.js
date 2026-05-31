const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const rootDir = path.resolve(__dirname, '..');
const homeWxml = fs.readFileSync(path.join(rootDir, 'pages', 'home', 'index.wxml'), 'utf8');

test('home page keeps a menu-first layout without extra guidance blocks', () => {
  assert.match(homeWxml, /本周主厨菜单/);
  assert.doesNotMatch(homeWxml, /Member Delivery/);
  assert.doesNotMatch(homeWxml, /立即预订明日餐食/);
  assert.doesNotMatch(homeWxml, /如何预订/);
});
