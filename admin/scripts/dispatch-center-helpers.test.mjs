import assert from "node:assert/strict";
import {
  buildDispatchWorkspaceNav,
  createEmptyNewRiderDraft,
  validateCreateRiderDraft,
  buildCreateRiderPayload,
  normalizeDispatchOverview,
  buildDispatchHeroMetrics,
  riderStatusLabel,
  riderStatusTagClass,
  groupBatchesByMealPeriod,
  getActiveQueueLabel
} from "../temp-test/modules/dispatch/dispatchCenterPage.helpers.js";

// Nav items
assert.deepEqual(
  buildDispatchWorkspaceNav(),
  [
    { label: "配送工作台", value: "" },
    { label: "区域管理", value: "areas" },
    { label: "骑手管理", value: "riders" }
  ]
);

// New rider draft
const draft = createEmptyNewRiderDraft();
assert.equal(draft.riderName, "");
assert.equal(draft.phone, "");
assert.equal(draft.password, "888888");

// Validate rider
assert.equal(validateCreateRiderDraft({ riderName: "", phone: "", password: "888888" }), "请填写骑手姓名");
assert.equal(validateCreateRiderDraft({ riderName: "张三", phone: "abc", password: "888888" }), "请填写正确的手机号");
assert.equal(validateCreateRiderDraft({ riderName: "张三", phone: "13800000001", password: "888888" }), null);

// Build payload
assert.deepEqual(
  buildCreateRiderPayload({ riderName: " 张三 ", phone: " 13800000001 ", password: " 888888 " }),
  { riderName: "张三", displayName: "张三", phone: "13800000001", password: "888888", employmentStatus: "ACTIVE", updatedBy: "老板" }
);

// Normalize overview
assert.deepEqual(normalizeDispatchOverview({}), {
  pendingLunchCount: 0,
  pendingDinnerCount: 0,
  pendingReminderCount: 0,
  pendingExceptionCount: 0,
  activeBatches: [],
  exceptions: []
});

// Hero metrics
const metrics = buildDispatchHeroMetrics({ pendingLunchCount: 5, pendingDinnerCount: 3, pendingReminderCount: 2, pendingExceptionCount: 1, activeBatches: [], exceptions: [] });
assert.equal(metrics.length, 4);
assert.equal(metrics[0].label, "午餐待配送");

// Status labels
assert.equal(riderStatusLabel("ACTIVE"), "启用中");
assert.equal(riderStatusLabel("DISABLED"), "已停用");
assert.equal(riderStatusTagClass("ACTIVE"), "tag-green");
assert.equal(riderStatusTagClass("DISABLED"), "tag-gray");

// Batch grouping
const groups = groupBatchesByMealPeriod([
  { mealPeriod: "LUNCH", batchId: 1, serveDate: "", riderProfileId: 1, riderName: "", areaCode: "", batchStatus: "", totalCount: 0, deliveredCount: 0, currentSequence: 0, currentCustomerName: "", nextCustomerName: "" },
  { mealPeriod: "DINNER", batchId: 2, serveDate: "", riderProfileId: 1, riderName: "", areaCode: "", batchStatus: "", totalCount: 0, deliveredCount: 0, currentSequence: 0, currentCustomerName: "", nextCustomerName: "" }
]);
assert.equal(groups.lunch.length, 1);
assert.equal(groups.dinner.length, 1);

// Queue label
assert.equal(getActiveQueueLabel({ currentCustomerName: "张三", nextCustomerName: "李四", batchId: 1, serveDate: "", mealPeriod: "", riderProfileId: 1, riderName: "", areaCode: "", batchStatus: "", totalCount: 0, deliveredCount: 0, currentSequence: 0 }), "张三 / 下一单 李四");

console.log("dispatch center helpers test: ok");
