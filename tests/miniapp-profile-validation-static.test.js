const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const profilePage = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'profile', 'index.js'), 'utf8');
const profileAuth = fs.readFileSync(path.join(repoRoot, 'miniapp', 'utils', 'profile-auth.js'), 'utf8');

assert.match(
  profilePage,
  /async submitProfileCompletion\(\)\s*\{[\s\S]*getSubmitProfileError\(\s*\{[\s\S]*mode:\s*'register'/,
  '顾客补全资料时也应复用统一姓名校验'
);
assert.match(profileAuth, /请输入正确的姓名/, '统一资料校验应提示非法姓名格式');

console.log('PASS: 顾客补全资料页已补齐姓名格式校验');
