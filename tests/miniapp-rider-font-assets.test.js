const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const appWxss = path.join(repoRoot, 'miniapp-rider', 'app.wxss');
const queueWxml = fs.readFileSync(path.join(repoRoot, 'miniapp-rider', 'pages', 'queue', 'index.wxml'), 'utf8');
const orderDetailWxml = fs.readFileSync(path.join(repoRoot, 'miniapp-rider', 'pages', 'order-detail', 'index.wxml'), 'utf8');
const content = fs.readFileSync(appWxss, 'utf8');

assert.equal(
  /fonts\.gstatic\.com|fonts\.googleapis\.com/.test(content),
  false,
  'miniapp-rider/app.wxss 仍然引用 Google 外链字体'
);

assert.doesNotMatch(
  content,
  /@font-face|material-symbols-outlined\.woff2|src:\s*url\(/,
  '骑手小程序不应继续通过本地字体文件加载 Material Symbols'
);

assert.doesNotMatch(
  `${queueWxml}\n${orderDetailWxml}`,
  />\s*(location_on|verified|add_a_photo|arrow_back|call|navigation|person|storefront|schedule|warning|hourglass_empty|error|image_not_supported|edit|undo|close|check_circle|info|block|touch_app|drag_indicator|inbox)\s*</,
  '骑手端页面不应再直接渲染 Material Symbols 的图标名称'
);

console.log('PASS: 骑手小程序已移除本地字体依赖');
