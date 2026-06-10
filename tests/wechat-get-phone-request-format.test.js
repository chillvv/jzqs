const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const wechatService = fs.readFileSync(
  path.join(
    repoRoot,
    'backend',
    'src',
    'main',
    'java',
    'com',
    'jzqs',
    'app',
    'common',
    'wechat',
    'WeChatService.java'
  ),
  'utf8'
);

assert.match(
  wechatService,
  /HttpHeaders\s+headers\s*=\s*new HttpHeaders\(\);[\s\S]*headers\.setContentType\(MediaType\.APPLICATION_JSON\);/,
  'WeChatService.getPhoneNumber 应显式设置 application/json 请求头'
);

assert.match(
  wechatService,
  /new HttpEntity<>\(requestBody,\s*headers\)/,
  'WeChatService.getPhoneNumber 应使用携带 JSON 请求头的 HttpEntity 调用微信接口'
);

console.log('PASS: 微信换手机号请求已显式使用 JSON 请求格式');
