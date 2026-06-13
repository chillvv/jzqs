const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const js = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'addresses', 'index.js'), 'utf8');
const wxml = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'addresses', 'index.wxml'), 'utf8');

assert.match(js, /loadCustomerProfile|loadCustomerContext/, '地址页应加载当前登录用户资料');
assert.match(js, /validateAddressForm/, '地址页应有独立的地址校验逻辑');
assert.match(js, /customerProfile/, '地址页应维护当前登录用户资料');
assert.doesNotMatch(wxml, /data-field="contactName"/, '地址页不应继续允许手填联系人姓名');
assert.doesNotMatch(wxml, /data-field="contactPhone"/, '地址页不应继续允许手填联系人手机号');
assert.match(wxml, /当前登录用户/, '地址页应明确展示当前登录用户信息');

console.log('PASS: 顾客地址页已绑定登录用户并补齐基础校验');
