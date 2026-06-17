import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const read = (relativePath) =>
  fs.readFileSync(path.join(process.cwd(), relativePath), "utf8");

test("aftersale ledger migration adds settlement and compensation columns", () => {
  const migration = read("backend/src/main/resources/db/migration/V46__aftersale_ledger_settlement.sql");
  assert.match(migration, /ADD COLUMN source_category VARCHAR\(32\)/);
  assert.match(migration, /ADD COLUMN issue_param_summary VARCHAR\(255\)/);
  assert.match(migration, /ADD COLUMN estimated_loss_meals INT NOT NULL DEFAULT 0/);
  assert.match(migration, /ADD COLUMN settled_loss_meals INT NOT NULL DEFAULT 0/);
  assert.match(migration, /ADD COLUMN gift_zero_meal_count INT NOT NULL DEFAULT 0/);
  assert.match(migration, /ADD COLUMN gift_veggie_juice_count INT NOT NULL DEFAULT 0/);
});

test("aftersale api exposes ledger filters and order options endpoint", () => {
  const controller = read("backend/src/main/java/com/jzqs/app/aftersale/api/AftersaleController.java");
  const createRequest = read("backend/src/main/java/com/jzqs/app/aftersale/api/AdminAftersaleCreateRequest.java");
  const resolveRequest = read("backend/src/main/java/com/jzqs/app/aftersale/api/AdminAftersaleResolveRequest.java");
  const listResponse = read("backend/src/main/java/com/jzqs/app/aftersale/api/AdminAftersaleListItemResponse.java");

  assert.match(controller, /@GetMapping\("\/order-options"\)/);
  assert.match(controller, /@RequestParam\(required = false\) String startDate/);
  assert.match(controller, /@RequestParam\(required = false\) Boolean hideAutoRefund/);
  assert.match(createRequest, /String issueParamSummary/);
  assert.match(createRequest, /int estimatedLossMeals/);
  assert.match(createRequest, /String sourceCategory/);
  assert.match(resolveRequest, /int settledLossMeals/);
  assert.match(resolveRequest, /int giftZeroMealCount/);
  assert.match(resolveRequest, /int giftVeggieJuiceCount/);
  assert.match(listResponse, /String issueParamSummary/);
  assert.match(listResponse, /int estimatedLossMeals/);
});

test("aftersale service stores ledger fields and filters auto refunds from normal views", () => {
  const service = read("backend/src/main/java/com/jzqs/app/aftersale/service/impl/AftersaleServiceImpl.java");
  assert.match(service, /source_category/);
  assert.match(service, /issue_param_summary/);
  assert.match(service, /estimated_loss_meals/);
  assert.match(service, /settled_loss_meals/);
  assert.match(service, /gift_zero_meal_count/);
  assert.match(service, /gift_veggie_juice_count/);
  assert.match(service, /hideAutoRefund/);
  assert.match(service, /orderOptions\(String serveDate\)/);
});

test("aftersale refund completion does not reference outer request state", () => {
  const service = read("backend/src/main/java/com/jzqs/app/aftersale/service/impl/AftersaleServiceImpl.java");
  const completeRefundCaseBlock = service.match(/private void completeRefundCase\([\s\S]*?\n    \}/);

  assert.ok(completeRefundCaseBlock, "should contain completeRefundCase implementation");
  assert.doesNotMatch(
    completeRefundCaseBlock[0],
    /request\./,
    "completeRefundCase should not reference request variables outside its own parameters"
  );
});

test("aftersale parameterized queries avoid deprecated rowMapper-first overload style", () => {
  const service = read("backend/src/main/java/com/jzqs/app/aftersale/service/impl/AftersaleServiceImpl.java");
  assert.doesNotMatch(
    service,
    /jdbcTemplate\.query\(\s*""".*?\(rs,\s*rowNum\)\s*->[\s\S]*?\)\s*,\s*[a-zA-Z]/,
    "parameterized query calls should not use rowMapper-first overload style with trailing args"
  );
});

test("admin aftersale page has ledger and settlement views with order options fetch", () => {
  const page = read("admin/src/modules/aftersales/AftersalePage.tsx");
  const http = read("admin/src/shared/api/http.ts");
  const ledgerViewMatches = page.match(/登记台账/g) ?? [];
  const settlementViewMatches = page.match(/处理台账/g) ?? [];
  assert.match(page, /售后登记/);
  assert.match(page, /处理台账/);
  assert.doesNotMatch(page, /<div className="stat-title">售后处理<\/div>/, "售后台账顶部不应再保留重复的售后处理卡片标题");
  assert.equal(ledgerViewMatches.length, 1, "售后台账不应重复展示登记台账切换入口");
  assert.equal(settlementViewMatches.length, 1, "售后台账不应重复展示处理台账切换入口");
  assert.match(page, /隐藏秒退款/);
  assert.match(page, /补零餐/);
  assert.match(page, /果蔬汁/);
  assert.match(http, /order-options\?serveDate=/);
  assert.match(page, /<DatePicker/, "售后台账应复用统一 DatePicker 组件");
  assert.doesNotMatch(page, /type="date"/, "售后台账不应继续使用原生 date input");
});

test("admin layout uses settlement naming and places aftersale before dispatch", () => {
  const layout = read("admin/src/app/layout/AdminLayout.tsx");
  assert.match(layout, /售后台账/);
  assert.doesNotMatch(layout, /售后中心/);

  const aftersaleIndex = layout.indexOf('key: "/aftersales"');
  const dispatchIndex = layout.indexOf('key: "/dispatch"');
  assert.ok(aftersaleIndex >= 0, "should contain aftersale nav item");
  assert.ok(dispatchIndex >= 0, "should contain dispatch nav item");
  assert.ok(aftersaleIndex < dispatchIndex, "aftersale nav item should appear before dispatch");
});
