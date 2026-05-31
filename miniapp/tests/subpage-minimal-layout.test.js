const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const rootDir = path.join(__dirname, '..', 'pages');
const receiptsWxml = fs.readFileSync(path.join(rootDir, 'receipts', 'index.wxml'), 'utf8');
const addressesWxml = fs.readFileSync(path.join(rootDir, 'addresses', 'index.wxml'), 'utf8');
const walletWxml = fs.readFileSync(path.join(rootDir, 'wallet', 'index.wxml'), 'utf8');

test('receipt page removes top bilingual guidance blocks', () => {
  assert.match(receiptsWxml, /每日送达回执/);
  assert.doesNotMatch(receiptsWxml, /Delivery Receipt/);
  assert.doesNotMatch(receiptsWxml, /这里只展示已送达且已生成回执信息的配送订单/);
});

test('address page keeps only the essential title block', () => {
  assert.match(addressesWxml, /收餐地址管理/);
  assert.doesNotMatch(addressesWxml, /Address Book/);
  assert.doesNotMatch(addressesWxml, /新增或切换默认地址后/);
});

test('wallet page keeps only the essential title block', () => {
  assert.match(walletWxml, /餐次流水单/);
  assert.doesNotMatch(walletWxml, /Meal Balance/);
  assert.doesNotMatch(walletWxml, /这里会记录下单扣减、退款退回和补偿补回的餐次变化/);
});
