const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const aftersaleWxml = fs.readFileSync(path.join(__dirname, '..', 'pages', 'aftersale-apply', 'index.wxml'), 'utf8');
const ordersJs = fs.readFileSync(path.join(__dirname, '..', 'pages', 'orders', 'index.js'), 'utf8');
const aftersaleUtils = fs.readFileSync(path.join(__dirname, '..', 'utils', 'aftersale.js'), 'utf8');

test('aftersale page keeps the form simple without extra guidance copy', () => {
  assert.match(aftersaleWxml, /申请售后/);
  assert.doesNotMatch(aftersaleWxml, /After Sale/);
  assert.doesNotMatch(aftersaleWxml, /提交后商家会核对订单与配送情况/);
});

test('orders page shows a concise rejected aftersale detail message', () => {
  assert.match(ordersJs, /buildRejectedAftersaleDetail/);
  assert.match(aftersaleUtils, /售后申请未通过/);
  assert.doesNotMatch(aftersaleUtils, /售后申请已被驳回/);
});
