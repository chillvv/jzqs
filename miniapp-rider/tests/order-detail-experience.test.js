const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const detailJs = fs.readFileSync(path.join(__dirname, '..', 'pages', 'order-detail', 'index.js'), 'utf8');
const detailWxml = fs.readFileSync(path.join(__dirname, '..', 'pages', 'order-detail', 'index.wxml'), 'utf8');

test('order detail supports receipt and exception flow', () => {
  assert.match(detailJs, /enterEditReceiptMode/);
  assert.match(detailJs, /reportException/);
  assert.match(detailWxml, /送达后请及时上传回执照片，顾客端会同步看到送达结果。/);
});
