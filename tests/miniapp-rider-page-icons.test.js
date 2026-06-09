const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const queueWxml = fs.readFileSync(path.join(repoRoot, 'miniapp-rider', 'pages', 'queue', 'index.wxml'), 'utf8');
const orderDetailWxml = fs.readFileSync(path.join(repoRoot, 'miniapp-rider', 'pages', 'order-detail', 'index.wxml'), 'utf8');

const requiredIconFiles = [
  'back.svg',
  'info-circle.svg',
  'blocked.svg',
  'touch.svg',
  'location.svg',
  'drag-handle.svg',
  'empty-box.svg',
  'phone.svg',
  'navigation.svg',
  'user.svg',
  'store.svg',
  'verified.svg',
  'image-off.svg',
  'edit.svg',
  'undo.svg',
  'close.svg',
  'camera.svg',
  'clock.svg',
  'warning.svg'
];

for (const name of requiredIconFiles) {
  const absolutePath = path.join(repoRoot, 'miniapp-rider', 'assets', 'icons', name);
  assert.equal(fs.existsSync(absolutePath), true, `缺少图标资源 ${name}`);
}

assert.match(queueWxml, /assets\/icons\/info-circle\.svg/, '队列页待审核提示应使用 info-circle.svg');
assert.match(queueWxml, /assets\/icons\/blocked\.svg/, '队列页停用提示应使用 blocked.svg');
assert.match(queueWxml, /assets\/icons\/location\.svg/, '队列页地址应使用 location.svg');
assert.match(queueWxml, /assets\/icons\/drag-handle\.svg/, '队列页拖拽应使用 drag-handle.svg');
assert.match(queueWxml, /assets\/icons\/empty-box\.svg/, '队列页空状态应使用 empty-box.svg');

assert.match(orderDetailWxml, /assets\/icons\/back\.svg/, '详情页返回应使用 back.svg');
assert.match(orderDetailWxml, /assets\/icons\/phone\.svg/, '详情页联系应使用 phone.svg');
assert.match(orderDetailWxml, /assets\/icons\/navigation\.svg/, '详情页导航应使用 navigation.svg');
assert.match(orderDetailWxml, /assets\/icons\/camera\.svg/, '详情页上传照片应使用 camera.svg');
assert.match(orderDetailWxml, /assets\/icons\/warning\.svg/, '详情页异常按钮应使用 warning.svg');

console.log('PASS: 骑手端关键页面已切换为本地图标资源');
