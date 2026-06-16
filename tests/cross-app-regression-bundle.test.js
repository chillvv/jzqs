import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const read = (relativePath) =>
  fs.readFileSync(path.join(process.cwd(), relativePath), "utf8");

test("backend bundle wires opened_at, address guard, and rider attention sources", () => {
  const packageGrant = read("backend/src/main/java/com/jzqs/app/packageplan/service/impl/PackageGrantServiceImpl.java");
  const customerAsset = read("backend/src/main/java/com/jzqs/app/customer/service/impl/CustomerAssetServiceImpl.java");
  const subscriptionService = read("backend/src/main/java/com/jzqs/app/subscription/service/impl/SubscriptionRuleServiceImpl.java");
  const mobilePortal = read("backend/src/main/java/com/jzqs/app/mobile/MobilePortalServiceImpl.java");
  const deliveryService = read("backend/src/main/java/com/jzqs/app/delivery/service/impl/DeliveryServiceImpl.java");

  assert.match(packageGrant, /opened_at = COALESCE\(opened_at, CURRENT_TIMESTAMP\)|SET opened_at = COALESCE\(opened_at, CURRENT_TIMESTAMP\)/);
  assert.match(packageGrant, /insertWalletTransaction\([^\n]*"OPEN"/);
  assert.match(customerAsset, /openedAt|opened_at/);
  assert.match(subscriptionService, /SELECT COUNT\(\*\) FROM customer_addresses WHERE customer_id = \?/);
  assert.match(subscriptionService, /客户暂无地址|请先补充地址/);
  assert.match(mobilePortal, /attentionLabel|hasAttentionMark|attentionSources/);
  assert.match(deliveryService, /Instant\.parse|OffsetDateTime\.parse|parseTimestamp/);
});

test("admin and miniapps expose the repaired user-facing entries", () => {
  const progressPage = read("admin/src/modules/dispatch/DispatchProgressPage.tsx");
  const ridersPage = read("admin/src/modules/dispatch/DispatchRidersPage.tsx");
  const menuPage = read("admin/src/modules/menu/MenuSchedulePage.tsx");
  const subscriptionForm = read("admin/src/modules/orders/SubscriptionRuleForm.tsx");
  const orderPrepPage = read("admin/src/modules/orders/OrderPrepPage.tsx");
  const loginPage = read("admin/src/modules/auth/AdminLoginPage.tsx");
  const adminRealtime = read("admin/src/shared/realtime/adminRealtime.ts");
  const customerPage = read("admin/src/modules/customers/CustomerAssetPage.tsx");
  const customerProfile = read("miniapp/pages/profile/index.wxml");
  const customerProfileStyle = read("miniapp/pages/profile/index.wxss");
  const customerWalletPage = read("miniapp/pages/wallet/index.wxml");
  const customerWalletUtils = read("miniapp/utils/aftersale.js");
  const riderQueueJs = read("miniapp-rider/pages/queue/index.js");
  const riderQueueWxml = read("miniapp-rider/pages/queue/index.wxml");

  assert.match(progressPage, /\bitem\.totalCount > 1\b|\bitem\.totalCount\s*\?\s*`\$\{item\.totalCount\}/);
  assert.match(ridersPage, /riderName:\s*draft\.riderName\.trim\(\)/);
  assert.match(ridersPage, /nameInputRef\.current\?\.select\(\)/);
  assert.match(menuPage, /await saveMenuWeekDay\(week\.weekId, serveDate, drafts\[serveDate\]\)/);
  assert.match(menuPage, /await persistDraftDaysBeforePublish\(week\.weekId\)/);
  assert.match(subscriptionForm, /该客户暂无地址|请先去客户地址管理补充/);
  assert.doesNotMatch(subscriptionForm, /不指定（使用客户默认地址）|不指定.*默认地址/);
  assert.doesNotMatch(orderPrepPage, /<MoreHorizontal size=\{16\} \/>/);
  assert.match(orderPrepPage, /查看详情/);
  assert.match(orderPrepPage, /编辑订单/);
  assert.match(orderPrepPage, /售后处理/);
  assert.match(orderPrepPage, /删除订单/);
  assert.doesNotMatch(orderPrepPage, /displayStatus === "PENDING_DISPATCH"|displayStatus === "DISPATCHING"/);
  assert.match(loginPage, /忘记密码|联系管理员/);
  assert.match(loginPage, /忘记密码[\s\S]{0,200}button|button[\s\S]{0,200}忘记密码|onClick[\s\S]{0,200}联系管理员/);
  assert.match(adminRealtime, /const createdSocket = new WebSocket\(resolveRealtimeUrl\(\)\)/);
  assert.match(adminRealtime, /createdSocket\.addEventListener\("open"/);
  assert.doesNotMatch(adminRealtime, /socket\?\.send\(JSON\.stringify\(\{ type: "AUTH", token, client: "admin" \}\)\)/);
  assert.match(customerPage, /<th>餐包有效期<\/th>|餐包有效期/);
  assert.doesNotMatch(customerPage, /商家备注[\s\S]*有效期：/);
  assert.match(customerProfile, /vip-submeta|remainingValidityDays/);
  assert.doesNotMatch(customerProfile, /简知会员|vip-expiry-pill/);
  assert.match(customerProfileStyle, /\.vip-submeta/);
  assert.match(customerProfileStyle, /\.vip-validity-line/);
  assert.match(customerProfileStyle, /\.vip-validity-days/);
  assert.match(customerWalletPage, /displayCreatedAt|开卡/);
  assert.match(customerWalletUtils, /transactionType === "OPEN"|return "开卡"/);
  assert.match(customerWalletUtils, /年.*月.*日|formatChineseDate/);
  assert.match(riderQueueJs, /attentionLabel|hasAttentionMark|needAttention/);
  assert.match(riderQueueWxml, /需留意/);
});
