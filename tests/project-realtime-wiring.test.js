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

test('admin vite dev server proxies realtime websocket traffic', () => {
  const viteConfig = read('admin/vite.config.ts');
  assert.match(viteConfig, /"\/ws"/);
  assert.match(viteConfig, /target:\s*"http:\/\/localhost:8081"/);
});

test('admin uses shared realtime client from layout shell', () => {
  const layout = read('admin/src/app/layout/AdminLayout.tsx');
  assert.match(layout, /connectAdminRealtime|useAdminRealtime|startAdminRealtime/);
});

test('admin sidebar uses four-character labels and shows rider center before aftersales', () => {
  const layout = read('admin/src/app/layout/AdminLayout.tsx');
  assert.match(layout, /label:\s*"骑手中心"/);
  assert.match(layout, /label:\s*"售后中心"/);
  assert.match(layout, /label:\s*"客户资产"/);
  assert.match(layout, /label:\s*"菜单配置"/);
  assert.match(layout, /label:\s*"系统设置"/);
  assert.ok(layout.indexOf('label: "骑手中心"') < layout.indexOf('label: "售后中心"'));
});

test('dispatch area bindings do not filter out newly created empty areas', () => {
  const service = read('backend/src/main/java/com/jzqs/app/dispatch/service/impl/DispatchServiceImpl.java');
  assert.doesNotMatch(service, /filter\(binding -> !binding\.orders\(\)\.isEmpty\(\)\)/);
});

test('admin dispatch areas page refreshes from realtime dispatch events', () => {
  const page = read('admin/src/modules/dispatch/DispatchAreasPage.tsx');
  assert.match(page, /useAdminRealtime/);
  assert.match(page, /message\.eventType/);
  assert.match(page, /startsWith\("dispatch\."\)/);
  assert.match(page, /reload\(\)/);
});

test('rider miniapp wires app level realtime helper', () => {
  const appJs = read('miniapp-rider/app.js');
  assert.match(appJs, /realtime/);
});

test('customer miniapp wires app level realtime helper', () => {
  const appJs = read('miniapp/app.js');
  assert.match(appJs, /realtime/);
});

test('customer orders page refreshes when customer realtime events arrive', () => {
  const ordersPage = read('miniapp/pages/orders/index.js');
  assert.match(ordersPage, /eventType\.startsWith\('customer\.'\)/);
  assert.match(ordersPage, /this\.loadOrders\(\)/);
});

test('rider receipt mutations publish customer order realtime updates', () => {
  const service = read('backend/src/main/java/com/jzqs/app/mobile/MobilePortalServiceImpl.java');
  assert.match(service, /publishCustomerEvent\("customer\.order\.changed", .*mealSlotOrderId\)/);
});

test('rider queue page refreshes when dispatch realtime events arrive', () => {
  const queuePage = read('miniapp-rider/pages/queue/index.js');
  assert.match(queuePage, /startsWith\('dispatch\.'\)/);
  assert.match(queuePage, /this\.loadQueue\(\{ silent: true \}\)/);
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
