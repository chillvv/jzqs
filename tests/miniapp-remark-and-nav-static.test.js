const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');

const profileWxml = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'profile', 'index.wxml'), 'utf8');
const profileJs = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'profile', 'index.js'), 'utf8');
const orderWxml = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'order', 'index.wxml'), 'utf8');
const orderJs = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'order', 'index.js'), 'utf8');

const pagesWithBanner = [
  'miniapp/pages/login/index.wxml',
  'miniapp/pages/orders/index.wxml',
  'miniapp/pages/aftersale-apply/index.wxml',
  'miniapp/pages/receipts/index.wxml',
  'miniapp/pages/addresses/index.wxml',
  'miniapp/pages/wallet/index.wxml'
].map((file) => ({
  file,
  content: fs.readFileSync(path.join(repoRoot, file), 'utf8')
}));

assert.equal(
  profileWxml.includes('默认下单备注'),
  false,
  '顾客端“我的”页面不应继续展示默认下单备注入口'
);

assert.equal(
  profileJs.includes('goEditDefaultRemark'),
  false,
  '顾客端“我的”页面不应继续保留默认备注编辑逻辑'
);

assert.match(
  orderWxml,
  /bindtap="toggleDefaultRemark"/,
  '顾客端下单页应展示设为默认入口'
);

assert.match(
  orderJs,
  /setDefaultRemark|saveDefaultRemark/,
  '顾客端下单页应支持在下单流程内设置默认备注'
);

for (const page of pagesWithBanner) {
  assert.match(
    page.content,
    /subpage-nav-bar|custom-nav-bar/,
    `${page.file} 应补齐顶部 Banner 通栏`
  );
  assert.match(
    page.content,
    /bindtap="goBack"/,
    `${page.file} 顶部 Banner 应提供返回入口`
  );
}

console.log('PASS: 顾客端默认备注已迁到下单页，二级页顶部 Banner 已补齐');
