const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');

function read(...segments) {
  return fs.readFileSync(path.join(repoRoot, ...segments), 'utf8');
}

const customerPage = read('admin', 'src', 'modules', 'customers', 'CustomerAssetPage.tsx');
const orderPrepPage = read('admin', 'src', 'modules', 'orders', 'OrderPrepPage.tsx');
const subscriptionForm = read('admin', 'src', 'modules', 'orders', 'SubscriptionRuleForm.tsx');
const manualCreateHelpers = read('admin', 'src', 'modules', 'orders', 'manualCreateOrder.helpers.ts');
const adminTypes = read('admin', 'src', 'shared', 'api', 'types.ts');
const adminHttp = read('admin', 'src', 'shared', 'api', 'http.ts');
const customerService = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'customer', 'service', 'impl', 'CustomerAssetServiceImpl.java');
const orderPrepService = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'order', 'service', 'impl', 'OrderPrepServiceImpl.java');
const subscriptionService = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'subscription', 'service', 'impl', 'SubscriptionRuleServiceImpl.java');

assert.equal(customerPage.includes('商家备注管理'), false, '客户中心不应保留多条备注管理块');
assert.equal(customerPage.includes('添加商家备注'), false, '客户中心不应保留新增备注入口');
assert.equal(customerPage.includes('保存备注'), false, '客户中心不应保留单独保存备注按钮');
assert.equal(customerPage.includes('商家备注'), true, '客户中心应保留单一商家备注字段');

assert.equal(orderPrepPage.includes('订单备注'), false, '后台录单不应继续显示订单备注');
assert.equal(orderPrepPage.includes('后台备注'), false, '后台页面不应继续显示后台备注');
assert.equal(orderPrepPage.includes('老板备注'), false, '后台页面不应继续显示老板备注');
assert.equal(orderPrepPage.includes('商家备注'), true, '后台订单页应统一显示商家备注');

assert.equal(subscriptionForm.includes('默认备注'), false, '固定订餐不应继续显示默认备注');
assert.equal(subscriptionForm.includes('商家备注'), true, '固定订餐应统一显示商家备注');

assert.equal(manualCreateHelpers.includes('note:'), false, '代客录单 helper 不应继续使用 note 字段');
assert.equal(manualCreateHelpers.includes('merchantRemark'), true, '代客录单 helper 应改用 merchantRemark 字段');

assert.equal(adminTypes.includes('adminNote'), false, '前端类型不应继续暴露 adminNote');
assert.equal(adminTypes.includes('defaultNote'), false, '前端类型不应继续暴露 defaultNote');
assert.equal(adminTypes.includes('merchantRemark'), true, '前端类型应统一暴露 merchantRemark');

assert.equal(adminHttp.includes('adminNote'), false, '前端接口不应继续发送 adminNote');
assert.equal(adminHttp.includes('defaultNote'), false, '前端接口不应继续发送 defaultNote');
assert.equal(adminHttp.includes('merchantRemark'), true, '前端接口应统一发送 merchantRemark');

assert.equal(customerService.includes('customerNotes('), false, '客户服务不应继续维护 customerNotes 链路');
assert.equal(customerService.includes('saveCustomerNote('), false, '客户服务不应继续维护 saveCustomerNote 链路');
assert.equal(orderPrepService.includes('admin_note'), false, '订单服务不应继续读取 admin_note');
assert.equal(subscriptionService.includes('default_note'), false, '固定订餐服务不应继续读取 default_note');

console.log('PASS: 商家备注链路已统一');
