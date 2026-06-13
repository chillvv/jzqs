const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const customerAssetPage = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'customers', 'CustomerAssetPage.tsx'), 'utf8');
const riderHelpers = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'dispatch', 'dispatchCenterLayout.helpers.ts'), 'utf8');
const riderRequest = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'dispatch', 'api', 'DispatchCreateRiderRequest.java'), 'utf8');

assert.match(customerAssetPage, /姓名已存在/, '后台客户建档应提示重复姓名');
assert.match(customerAssetPage, /手机号已存在/, '后台客户建档应提示重复手机号');
assert.match(customerAssetPage, /请填写正确的11位手机号/, '后台客户建档应拦截非法手机号');
assert.match(customerAssetPage, /请填写正确的客户姓名/, '后台客户建档应拦截非法姓名');
assert.match(riderHelpers, /请填写正确的骑手姓名/, '后台骑手建档应拦截非法姓名');
assert.match(riderRequest, /@Pattern/, '后台骑手创建请求应校验手机号和姓名');

console.log('PASS: 后台客户与骑手录入校验已补齐');
