const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const page = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'customers', 'CustomerAssetPage.tsx'), 'utf8');
const types = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'shared', 'api', 'types.ts'), 'utf8');

assert.match(
  page,
  /function buildEditForm[\s\S]*initialMeals:\s*["']0["']/,
  'buildEditForm 返回对象应包含 initialMeals 字段'
);
assert.match(
  types,
  /export type CustomerNoteItem = \{/,
  '共享类型文件应导出 CustomerNoteItem'
);

console.log('PASS: 后台客户页关键类型字段已补齐');
