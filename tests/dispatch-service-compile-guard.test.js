const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const filePath = path.join(
  repoRoot,
  'backend',
  'src',
  'main',
  'java',
  'com',
  'jzqs',
  'app',
  'dispatch',
  'service',
  'impl',
  'DispatchServiceImpl.java'
);

const raw = fs.readFileSync(filePath);
const content = raw.toString('utf8');

assert.notEqual(raw[0], 0xef, 'DispatchServiceImpl.java 不应包含 UTF-8 BOM');
assert.match(
  content,
  /insertNotification\(\(\(Number\) row\.get\("customer_id"\)\)\.longValue\(\), "DISPATCH_CONFIRM", "\{\\?"content\\?":\\?"[^"]*\\?"\}"\);/,
  'notifyCustomer 中的通知内容字符串应保持为完整合法的 Java 字符串'
);

assert.match(
  content,
  /"[^"\n]*" \+ areaCode \+ "[^"\n]*" \+ blockingOrders\.size\(\) \+ "[^"\n]*"/,
  '删除区域时的异常提示文案应保持为完整合法的 Java 字符串拼接'
);

assert.match(
  content,
  /\.replace\(" ", ""\)\.replace\("-", ""\)\.replace\("，", ""\)\.replace\(",", ""\);/,
  '地址指纹标准化逻辑应使用完整合法的 replace 字符串参数'
);

console.log('PASS: DispatchServiceImpl.java 编译保护检查通过');
