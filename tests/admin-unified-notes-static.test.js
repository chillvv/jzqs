const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(
  path.join(__dirname, '..', 'admin', 'src', 'modules', 'customers', 'CustomerAssetPage.tsx'),
  'utf8'
);

assert.ok(source.includes('长期用户备注'), 'customer note section missing');
assert.ok(source.includes('限时商家备注'), 'time-boxed merchant section missing');
assert.ok(!source.includes('defaultTagsText'), 'old tag input still present');

console.log('PASS: admin customer center uses unified note sections');
