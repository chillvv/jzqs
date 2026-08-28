const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const tabbarWxss = fs.readFileSync(path.join(__dirname, '..', 'custom-tab-bar', 'index.wxss'), 'utf8');

test('rider custom tab bar matches customer height and spacing rhythm', () => {
  assert.match(tabbarWxss, /height:\s*130rpx/);
  assert.match(tabbarWxss, /padding-bottom:\s*calc\(env\(safe-area-inset-bottom\)\s*\+\s*6rpx\)/);
  assert.match(tabbarWxss, /\.tab-icon\s*\{[\s\S]*width:\s*52rpx;/);
  assert.match(tabbarWxss, /\.tab-item\s*\{[\s\S]*padding-top:\s*10rpx;/);
});
