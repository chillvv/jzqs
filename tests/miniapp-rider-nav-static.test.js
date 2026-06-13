const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');

const pagesWithBanner = [
  'miniapp-rider/pages/login/index.wxml',
  'miniapp-rider/pages/register/index.wxml',
  'miniapp-rider/pages/today/index.wxml',
  'miniapp-rider/pages/pending/index.wxml',
  'miniapp-rider/pages/blocked/index.wxml',
  'miniapp-rider/pages/order-detail/index.wxml'
].map((file) => ({
  file,
  content: fs.readFileSync(path.join(repoRoot, file), 'utf8')
}));

for (const page of pagesWithBanner) {
  assert.match(
    page.content,
    /subpage-nav-bar|header-bar/,
    `${page.file} 应补齐顶部 Banner 通栏`
  );
  assert.match(
    page.content,
    /bindtap="goBack"/,
    `${page.file} 顶部 Banner 应提供返回入口`
  );
}

console.log('PASS: 骑手端跳转页顶部 Banner 已补齐');
