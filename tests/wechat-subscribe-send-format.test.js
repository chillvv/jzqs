const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const service = fs.readFileSync(
  path.join(repoRoot, 'backend/src/main/java/com/jzqs/app/common/wechat/WeChatService.java'),
  'utf8'
);

assert.match(
  service,
  /HttpHeaders headers = new HttpHeaders\(\);[\s\S]*headers\.setContentType\(MediaType\.APPLICATION_JSON\);[\s\S]*HttpEntity<String> requestEntity = new HttpEntity<>\(requestBody, headers\);[\s\S]*restTemplate\.postForObject\(url, requestEntity, String\.class\);/,
  'sendDeliverySubscribeMessage 应显式按 JSON 方式调用微信订阅消息发送接口'
);

assert.match(
  service,
  /catch \(HttpStatusCodeException e\) \{[\s\S]*发送微信订阅消息 HTTP 异常[\s\S]*发送订阅消息失败：/,
  'sendDeliverySubscribeMessage 应保留微信 HTTP 异常明细，避免只返回笼统失败'
);

console.log('PASS: WeChat 订阅消息发送请求格式守卫通过');
