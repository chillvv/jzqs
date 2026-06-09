const assert = require('node:assert/strict');
const path = require('node:path');

const riderApiBase = require(path.join(__dirname, '..', 'miniapp-rider', 'utils', 'api-base.js'));
const customerApiBase = require(path.join(__dirname, '..', 'miniapp', 'utils', 'api-base.js'));

function testUsesProductionDomainByDefault() {
  assert.equal(
    riderApiBase.DEFAULT_API_BASE_URL,
    'https://jzqs.top',
    '骑手端默认基址应保持为线上域名'
  );
  assert.equal(
    customerApiBase.DEFAULT_API_BASE_URL,
    'https://jzqs.top',
    '顾客端默认基址应保持为线上域名'
  );
}

function testKeepsStoredCustomBaseUrl() {
  assert.equal(
    riderApiBase.resolveApiBaseUrl(' https://gray.jzqs.top/ '),
    'https://gray.jzqs.top',
    '骑手端应保留已存储的自定义域名'
  );
  assert.equal(
    customerApiBase.resolveApiBaseUrl('https://gray.jzqs.top///'),
    'https://gray.jzqs.top',
    '顾客端应裁剪已存储域名尾部斜杠'
  );
}

testUsesProductionDomainByDefault();
testKeepsStoredCustomBaseUrl();

console.log('PASS: 小程序默认 API 基址正确');
