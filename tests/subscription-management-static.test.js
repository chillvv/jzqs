import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const read = (relativePath) =>
  fs.readFileSync(path.join(process.cwd(), relativePath), "utf8");

test("subscription management supports edit, re-enable, and delete semantics", () => {
  const page = read("admin/src/modules/orders/SubscriptionManagementTab.tsx");
  const form = read("admin/src/modules/orders/SubscriptionRuleForm.tsx");
  const http = read("admin/src/shared/api/http.ts");
  const service = read("backend/src/main/java/com/jzqs/app/subscription/service/impl/SubscriptionRuleServiceImpl.java");

  assert.match(page, /编辑/, "固定订餐管理页应保留编辑入口");
  assert.match(page, /重新启用|启用/, "固定订餐管理页应支持重新启用入口");
  assert.match(page, /确认删除固定订餐计划/, "固定订餐管理页应保留删除确认");
  assert.match(form, /编辑固定订餐计划/, "固定订餐表单应支持编辑态");
  assert.match(http, /\/api\/admin\/subscription-rules\/\$\{id\}\/toggle/, "前端应保留固定订餐启停接口");
  assert.match(service, /setPaused\(!entity\.getPaused\(\)\)/, "后端应通过 paused 切换启停，不应只能单向停用");
  assert.doesNotMatch(service, /return Map\.of\("id", id, "status", "DELETED"\)/, "删除接口不应继续伪装成已删除但只返回假状态");
  assert.doesNotMatch(page, /已暂停/, "固定订餐页不应再把停用拆成已暂停和已停用两套前端文案");
  assert.match(page, /value="STOPPED"/, "固定订餐页应把停用筛选收口为单一 stopped 视图");
  assert.match(page, /subscription-status-pill/, "固定订餐页状态栏应使用更清爽的专用样式");
  assert.match(page, /subscription-rule-meal/, "固定订餐页应把午餐和晚餐拆成独立餐次卡片，而不是混在一行里");
  assert.match(page, /subscription-rule-actions/, "固定订餐页操作按钮应使用统一按钮组样式");
  assert.doesNotMatch(page, /tag tag-orange">\{item\.lunchQuantity\} 份|tag tag-green">\{item\.dinnerQuantity\} 份/, "固定订餐页不应继续用零散 tag 直接堆午晚餐餐数");
  assert.match(page, /subscription-meal-toggle/, "固定订餐页应使用午餐晚餐切换视图");
  assert.doesNotMatch(page, /<th>午餐<\/th>\s*<th>晚餐<\/th>/, "固定订餐页不应继续同时展示午餐和晚餐两列");
  assert.doesNotMatch(page, /subscription-rule-meal__title/, "固定订餐页当前已按午餐晚餐切换后，不应再在单元格内重复显示午晚餐标题");
  assert.match(page, /<th>生效周期<\/th>/, "固定订餐页时间段列表头应升级为更专业的生效周期");
  assert.match(page, /formatSubscriptionDateRange/, "固定订餐页应统一格式化固定订餐生效周期");
});
