const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const service = fs.readFileSync(
  path.join(
    repoRoot,
    'backend',
    'src',
    'main',
    'java',
    'com',
    'jzqs',
    'app',
    'order',
    'service',
    'impl',
    'OrderPrepServiceImpl.java'
  ),
  'utf8'
);

assert.match(service, /searchManualCreateCustomers/, '应保留录单客户搜索入口');
assert.doesNotMatch(service, /COALESCE\(\(SELECT mw\.total_meals/, '录单客户搜索不应继续依赖可能返回多行的餐包子查询');
assert.match(service, /LEFT JOIN[\s\S]*meal_wallets/, '录单客户搜索应改为关联餐包表查询');
assert.match(service, /COALESCE\(c\.name, ''\)|COALESCE\(c\.phone, ''\)/, '录单客户搜索 SQL 应对空姓名或空手机号兜底');
assert.match(
  service,
  /COALESCE\(mw\.total_meals, 0\) - COALESCE\(mw\.reserved_meals, 0\) - COALESCE\(mw\.consumed_meals, 0\)/,
  '录单客户剩余餐次计算应避免空值导致异常'
);

console.log('PASS: 录单客户搜索 SQL 已补齐空值兜底');
