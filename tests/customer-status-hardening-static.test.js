const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const customerAsset = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'customer', 'service', 'impl', 'CustomerAssetServiceImpl.java'), 'utf8');
const authService = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'auth', 'AuthService.java'), 'utf8');
const migration = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'resources', 'db', 'migration', 'V44__migrate_customer_status_to_formal_or_dormant.sql'), 'utf8');

assert.match(customerAsset, /normalizeCustomerStatus/, '客户资产服务应统一收口客户状态');
assert.doesNotMatch(customerAsset, /"INTENTION"/, '客户资产服务不应再把 INTENTION 当作默认状态');
assert.doesNotMatch(authService, /"INTENTION"/, '后台认证建档逻辑不应再写入 INTENTION');
assert.match(migration, /customer_status = 'FORMAL'/, '迁移脚本应把旧状态统一改成 FORMAL');
assert.match(migration, /customer_status = 'INTENTION'/, '迁移脚本应覆盖旧 INTENTION 数据');

console.log('PASS: 客户状态已收口为 FORMAL / DORMANT');
