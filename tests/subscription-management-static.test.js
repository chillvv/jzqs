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
});
