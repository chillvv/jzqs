const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const service = fs.readFileSync(
  path.join(repoRoot, 'backend/src/main/java/com/jzqs/app/common/wechat/WeChatService.java'),
  'utf8'
);

assert.match(
  service,
  /"thing2", Map\.of\("value", normalizeThingValue\(merchantName\)\)/,
  'thing2 应在发送前收敛到微信 thing 类型允许的长度'
);

assert.match(
  service,
  /"thing11", Map\.of\("value", normalizeThingValue\(hint\)\)/,
  'thing11 应在发送前收敛到微信 thing 类型允许的长度'
);

assert.match(
  service,
  /private String normalizeThingValue\(String value\) \{[\s\S]*return normalized\.length\(\) <= 20 \? normalized : normalized\.substring\(0, 20\);[\s\S]*\}/,
  'WeChatService 应提供 thing 类型长度归一化，避免超过 20 个字符被微信拒绝'
);

console.log('PASS: WeChat thing 字段长度守卫通过');
