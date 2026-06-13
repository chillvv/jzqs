const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');

const portal = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'MobilePortalServiceImpl.java'), 'utf8');
const authService = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'MobileAuthServiceImpl.java'), 'utf8');
const bindPhone = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'api', 'MobileBindPhoneRequest.java'), 'utf8');
const completeProfile = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'api', 'MobileCompleteProfileRequest.java'), 'utf8');
const phoneLogin = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'api', 'MobilePhoneLoginRequest.java'), 'utf8');
const addressUpsert = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'api', 'MobileAddressUpsertRequest.java'), 'utf8');

assert.match(portal, /resolveCustomerAddressContact/, '顾客地址保存应从当前顾客资料派生联系人');
assert.match(authService, /validateUniqueNickname/, '顾客移动端注册应校验重复姓名');
assert.match(authService, /validateUniqueCustomerPhone/, '顾客移动端注册应校验重复手机号');
assert.match(bindPhone, /@Pattern/, '顾客注册请求应校验手机号格式');
assert.match(completeProfile, /@Pattern|@Size/, '顾客补全资料请求应校验姓名格式');
assert.match(phoneLogin, /@Pattern/, '顾客手机号登录请求应校验手机号格式');
assert.match(addressUpsert, /@Pattern/, '顾客地址请求应校验手机号格式');

console.log('PASS: 顾客移动端请求和地址兜底校验已补齐');
