const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');

const read = (...segments) => fs.readFileSync(path.join(repoRoot, ...segments), 'utf8');

const projectConfig = JSON.parse(read('miniapp', 'project.config.json'));
const customerAuth = read('miniapp', 'utils', 'auth.js');
const mobileAuthService = read(
  'backend',
  'src',
  'main',
  'java',
  'com',
  'jzqs',
  'app',
  'mobile',
  'MobileAuthServiceImpl.java'
);
const envExample = read('.env.example');

assert.equal(
  projectConfig.appid,
  'wx68bc30e5dc1af89a',
  'miniapp/project.config.json 应切换到正式用户端 AppID'
);

assert.match(
  customerAuth,
  /this\.request\('\/api\/mobile\/auth\/wx-login', 'POST', \{\s*code\s*\}\)/,
  'miniapp/utils/auth.js 应继续通过 /api/mobile/auth/wx-login 发起静默登录'
);

assert.match(
  mobileAuthService,
  /WeChatService\.WeChatSession\s+session\s*=\s*weChatService\.code2Session\(code\);/,
  'MobileAuthServiceImpl.wxLogin 应通过微信 code2Session 获取真实 openid'
);

assert.match(
  mobileAuthService,
  /String\s+openid\s*=\s*session\.openid\(\);/,
  'MobileAuthServiceImpl.wxLogin 应使用 code2Session 返回的 openid'
);

assert.doesNotMatch(
  mobileAuthService,
  /String\s+openid\s*=\s*buildDevOpenid\(code\);/,
  'MobileAuthServiceImpl.wxLogin 不应继续拼接 dev openid'
);

assert.match(
  mobileAuthService,
  /return authState\(openid,\s*session\.sessionKey\(\),\s*true,\s*false,\s*false,\s*customerId\);/,
  'MobileAuthServiceImpl.wxLogin 对已注册用户应带上真实 session_key 返回登录态'
);

assert.match(
  mobileAuthService,
  /return authState\(openid,\s*session\.sessionKey\(\),\s*false,\s*true,\s*false,\s*null\);/,
  'MobileAuthServiceImpl.wxLogin 对未注册用户应带上真实 session_key 返回待绑定态'
);

assert.match(
  mobileAuthService,
  /private Map<String, Object> authState\(\s*String openid,\s*String sessionKey,/,
  'MobileAuthServiceImpl.authState 应接收 sessionKey 参数'
);

assert.match(
  mobileAuthService,
  /state\.put\("authMode",\s*AUTH_MODE_WECHAT\);/,
  'MobileAuthServiceImpl 应返回正式微信认证模式'
);

assert.equal(
  envExample.includes('WECHAT_DEV_MODE=false'),
  true,
  '.env.example 应默认关闭微信开发模拟模式'
);

console.log('PASS: 用户端微信一键登录已切到正式 AppID 与真实微信 openid 链路');
