const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const appJsonPath = path.join(repoRoot, 'miniapp-rider', 'app.json');
const tabBarWxmlPath = path.join(repoRoot, 'miniapp-rider', 'custom-tab-bar', 'index.wxml');
const tabBarJsPath = path.join(repoRoot, 'miniapp-rider', 'custom-tab-bar', 'index.js');
const queueWxmlPath = path.join(repoRoot, 'miniapp-rider', 'pages', 'queue', 'index.wxml');

const appJson = fs.readFileSync(appJsonPath, 'utf8');
const tabBarWxml = fs.readFileSync(tabBarWxmlPath, 'utf8');
const tabBarJs = fs.readFileSync(tabBarJsPath, 'utf8');
const queueWxml = fs.readFileSync(queueWxmlPath, 'utf8');

const iconFiles = [
  path.join(repoRoot, 'miniapp-rider', 'assets', 'icons', 'queue.png'),
  path.join(repoRoot, 'miniapp-rider', 'assets', 'icons', 'queue-active.png'),
  path.join(repoRoot, 'miniapp-rider', 'assets', 'icons', 'profile.png'),
  path.join(repoRoot, 'miniapp-rider', 'assets', 'icons', 'profile-active.png'),
  path.join(repoRoot, 'miniapp-rider', 'assets', 'icons', 'queue.svg'),
  path.join(repoRoot, 'miniapp-rider', 'assets', 'icons', 'queue-active.svg'),
  path.join(repoRoot, 'miniapp-rider', 'assets', 'icons', 'profile.svg'),
  path.join(repoRoot, 'miniapp-rider', 'assets', 'icons', 'profile-active.svg'),
  path.join(repoRoot, 'miniapp-rider', 'assets', 'icons', 'sort.svg'),
  path.join(repoRoot, 'miniapp-rider', 'assets', 'icons', 'check.svg')
];

assert.match(appJson, /assets\/icons\/queue\.png/, 'app.json 应改为引用 queue.png');
assert.match(appJson, /assets\/icons\/queue-active\.png/, 'app.json 应改为引用 queue-active.png');
assert.match(appJson, /assets\/icons\/profile\.png/, 'app.json 应改为引用 profile.png');
assert.match(appJson, /assets\/icons\/profile-active\.png/, 'app.json 应改为引用 profile-active.png');
assert.doesNotMatch(appJson, /tab-order|tab-profile/, 'app.json 不应再引用旧的 tab png 资源');
assert.doesNotMatch(appJson, /assets\/icons\/(?:queue|profile)(?:-active)?\.svg/, 'app.json 不应直接引用 svg 作为 tabBar 图标');

assert.match(tabBarWxml, /assets\/icons\/queue(?:-active)?\.svg/, '自定义 Tab 应使用 queue svg');
assert.match(tabBarWxml, /assets\/icons\/profile(?:-active)?\.svg/, '自定义 Tab 应使用 profile svg');
assert.doesNotMatch(tabBarWxml, /tab-order|tab-profile/, '自定义 Tab 不应再引用旧的 tab png 资源');

assert.match(tabBarJs, /assets\/icons\/queue(?:-active)?\.svg/, 'Tab 配置应使用 queue svg');
assert.match(tabBarJs, /assets\/icons\/profile(?:-active)?\.svg/, 'Tab 配置应使用 profile svg');
assert.doesNotMatch(tabBarJs, /tab-order|tab-profile/, 'Tab 配置不应再引用旧的 tab png 资源');

assert.doesNotMatch(
  queueWxml,
  /\{\{isEditMode \? 'check' : 'sort'\}\}/,
  '排序按钮不应再使用 sort/check 文字图标'
);
assert.match(queueWxml, /sort\.svg|check\.svg/, '排序按钮应改为本地 svg 图标');

for (const iconFile of iconFiles) {
  assert.equal(fs.existsSync(iconFile), true, `缺少图标资源: ${path.relative(repoRoot, iconFile)}`);
}

console.log('PASS: 骑手端 Tab 与排序按钮图标已切换为本地 SVG');
