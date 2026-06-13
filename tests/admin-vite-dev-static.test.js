const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const viteConfig = fs.readFileSync(path.join(repoRoot, 'admin', 'vite.config.ts'), 'utf8');

assert.match(viteConfig, /host:\s*(true|"0\.0\.0\.0"|'0\.0\.0\.0')/, 'Vite dev server 应显式开放 host');
assert.match(viteConfig, /target:\s*"http:\/\/localhost:8081"/, 'Vite proxy 应继续指向后端 8081');

console.log('PASS: 后台 Vite 开发服务配置已补齐');
