const fs = require('fs');
const path = require('path');
const test = require('node:test');
const assert = require('node:assert/strict');

const root = 'D:/Code/jzqs';

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('backend exposes unified realtime websocket endpoint', () => {
  const config = read('backend/src/main/java/com/jzqs/app/common/config/RealtimeWebSocketConfig.java');
  assert.match(config, /\/ws\/realtime/);
});

test('admin uses shared realtime client from layout shell', () => {
  const layout = read('admin/src/app/layout/AdminLayout.tsx');
  assert.match(layout, /connectAdminRealtime|useAdminRealtime|startAdminRealtime/);
});

test('rider miniapp wires app level realtime helper', () => {
  const appJs = read('miniapp-rider/app.js');
  assert.match(appJs, /realtime/);
});

test('customer miniapp wires app level realtime helper', () => {
  const appJs = read('miniapp/app.js');
  assert.match(appJs, /realtime/);
});

test('customer miniapp profile shows package validity summary', () => {
  const profileWxml = read('miniapp/pages/profile/index.wxml');
  assert.match(profileWxml, /到期日/);
  assert.match(profileWxml, /剩余天数/);
});

test('customer wallet page shows package validity summary', () => {
  const walletWxml = read('miniapp/pages/wallet/index.wxml');
  assert.match(walletWxml, /到期日/);
  assert.match(walletWxml, /提醒：/);
});

test('customer miniapp order page explains timed delivery subscription behavior', () => {
  const orderJs = read('miniapp/pages/order/index.js');
  assert.match(orderJs, /11:30/);
  assert.match(orderJs, /17:00/);
  assert.match(orderJs, /立即补发通知/);
});
