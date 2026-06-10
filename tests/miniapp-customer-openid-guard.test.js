const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const customerAuth = fs.readFileSync(
  path.join(repoRoot, 'miniapp', 'utils', 'auth.js'),
  'utf8'
);

assert.match(
  customerAuth,
  /async ensureOpenidReady\(\)\s*\{[\s\S]*if\s*\(this\.globalData\.openid\)\s*\{\s*return this\.globalData\.openid;[\s\S]*await this\.silentLogin\(\);[\s\S]*if\s*\(!this\.globalData\.openid\)\s*\{[\s\S]*throw new Error\('缺少微信身份标识，请重新进入小程序后重试'\);[\s\S]*return this\.globalData\.openid;[\s\S]*\}/,
  'miniapp/utils/auth.js 应在依赖 openid 的登录链路前补齐 ensureOpenidReady 兜底'
);

assert.match(
  customerAuth,
  /async phoneLogin\(phone\)\s*\{[\s\S]*const openid = await this\.ensureOpenidReady\(\);[\s\S]*openid,[\s\S]*phone[\s\S]*\}/,
  'miniapp/utils/auth.js 的手机号登录应先确保 openid 已就绪'
);

assert.match(
  customerAuth,
  /async register\(phone,\s*nickname\)\s*\{[\s\S]*const openid = await this\.ensureOpenidReady\(\);[\s\S]*openid,[\s\S]*phone,[\s\S]*nickname[\s\S]*\}/,
  'miniapp/utils/auth.js 的注册应先确保 openid 已就绪'
);

assert.match(
  customerAuth,
  /async completeProfile\(nickname\)\s*\{[\s\S]*const openid = await this\.ensureOpenidReady\(\);[\s\S]*openid,[\s\S]*nickname[\s\S]*\}/,
  'miniapp/utils/auth.js 的补全资料应先确保 openid 已就绪'
);

console.log('PASS: 顾客端依赖 openid 的认证接口已补齐静默登录兜底');
