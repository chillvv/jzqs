const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const page = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'customers', 'CustomerAssetPage.tsx'), 'utf8');
const helpers = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'customers', 'customerAssetPage.helpers.ts'), 'utf8');

assert.doesNotMatch(page, /意向客户|INTENTION/, '客户页不应再出现意向客户选项');
assert.doesNotMatch(helpers, /意向客户|INTENTION/, '客户页 helper 不应再保留意向客户映射');
assert.match(page, /正式客户/, '客户页应继续展示正式客户');
assert.match(page, /沉睡客户/, '客户页应继续展示沉睡客户');

console.log('PASS: 后台客户页已移除意向客户');
