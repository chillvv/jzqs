const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const mapper = fs.readFileSync(
  path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'admin', 'persistence', 'AdminRowMappers.java'),
  'utf8'
);
const response = fs.readFileSync(
  path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'order', 'api', 'OrderPrepItemResponse.java'),
  'utf8'
);

assert.match(response, /String referenceImageUrl,/);
assert.match(response, /String receiptUrl,/);
assert.match(response, /String receiptNote,/);
assert.match(response, /String deliveredAt/);

assert.match(mapper, /rs\.getString\("wallet_status_label"\)/, 'mapper 应继续映射钱包状态');
assert.match(mapper, /rs\.getString\("reference_image_url"\)/, 'mapper 必须映射参照图字段');
assert.match(mapper, /rs\.getString\("receipt_url"\)/, 'mapper 必须映射回执图字段');
assert.match(mapper, /rs\.getString\("receipt_note"\)/, 'mapper 必须映射回执备注字段');
assert.match(mapper, /rs\.getString\("delivered_at"\)/, 'mapper 必须映射送达时间字段');

console.log('PASS: OrderPrepItemResponse 构造参数已与后台 mapper 同步');
