const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

const profileJs = read('miniapp/pages/profile/index.js');
const profileWxml = read('miniapp/pages/profile/index.wxml');
const controller = read('backend/src/main/java/com/jzqs/app/mobile/api/MobileCustomerController.java');
const serviceImpl = read('backend/src/main/java/com/jzqs/app/mobile/MobilePortalServiceImpl.java');

assert.match(profileWxml, /测试订阅消息/, '“我的”页应提供测试订阅消息入口');
assert.match(profileJs, /wx\.requestSubscribeMessage/, '测试入口必须调用微信官方订阅弹窗');
assert.match(profileJs, /acceptWithAudio/, '前端应兼容 acceptWithAudio');
assert.match(profileJs, /acceptWithAlert/, '前端应兼容 acceptWithAlert');
assert.match(profileJs, /\/api\/mobile\/customer\/subscribe-message\/test-send/, '前端授权成功后应请求后端测试发送接口');
assert.doesNotMatch(profileJs, /showDeliverySubscriptionHint/, '测试入口不应插入自定义说明弹层冒充官方弹窗');

assert.match(controller, /@PostMapping\(\"\/subscribe-message\/test-send\"\)/, '后端应提供测试发送接口');
assert.match(serviceImpl, /pages\/profile\/index/, '测试消息跳转页应回到“我的”页');
assert.match(serviceImpl, /acceptWithAudio/, '后端应接受 acceptWithAudio');
assert.match(serviceImpl, /acceptWithAlert/, '后端应接受 acceptWithAlert');

console.log('PASS: 我的页订阅消息测试入口链路已接入');
