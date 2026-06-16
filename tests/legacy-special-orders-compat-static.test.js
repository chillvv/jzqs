const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const compatControllerPath = path.join(
  repoRoot,
  'backend',
  'src',
  'main',
  'java',
  'com',
  'jzqs',
  'app',
  'order',
  'api',
  'OrderPrepLegacyCompatibilityController.java'
);

const source = fs.readFileSync(compatControllerPath, 'utf8');

assert.match(source, /@RequestMapping\("\/api\/admin\/orders"\)/, '兼容控制器应挂在订单后台路由下');
assert.match(source, /@GetMapping\("\/special-orders"\)/, '兼容控制器应兜底旧 special-orders 请求');
assert.match(source, /Collections\.emptyList\(\)/, '特殊单已删除，兼容接口应返回空列表而不是继续构造业务数据');

console.log('PASS: 旧 special-orders 请求已有空结果兼容接口');
