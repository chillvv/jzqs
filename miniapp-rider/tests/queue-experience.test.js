const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const queueWxml = fs.readFileSync(path.join(__dirname, '..', 'pages', 'queue', 'index.wxml'), 'utf8');

test('queue page exposes clear delivery states', () => {
  assert.match(queueWxml, /待配送/);
  assert.match(queueWxml, /已完成/);
  assert.match(queueWxml, /长按可调整配送顺序，已完成订单会自动沉底展示。/);
});
