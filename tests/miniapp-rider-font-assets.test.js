const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const appWxss = path.join(repoRoot, 'miniapp-rider', 'app.wxss');
const localFont = path.join(
  repoRoot,
  'miniapp-rider',
  'assets',
  'fonts',
  'material-symbols-outlined.woff2'
);
const content = fs.readFileSync(appWxss, 'utf8');

assert.equal(
  /fonts\.gstatic\.com|fonts\.googleapis\.com/.test(content),
  false,
  'miniapp-rider/app.wxss 仍然引用 Google 外链字体'
);

assert.equal(
  fs.existsSync(localFont),
  true,
  '骑手小程序缺少本地图标字体资源 material-symbols-outlined.woff2'
);

console.log('PASS: 骑手小程序字体资源已本地化');
