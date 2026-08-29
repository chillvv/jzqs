const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const queueWxml = fs.readFileSync(path.join(__dirname, '..', 'pages', 'queue', 'index.wxml'), 'utf8');
const queueJs = fs.readFileSync(path.join(__dirname, '..', 'pages', 'queue', 'index.js'), 'utf8');

test('queue page exposes clear delivery states', () => {
  assert.match(queueWxml, /待配送/);
  assert.match(queueWxml, /已完成/);
  assert.match(queueWxml, /点击右侧箭头调整配送顺序/);
});

test('queue attention fallback covers backend merchantRemark', () => {
  assert.match(queueJs, /item\.merchantRemark/);
});
