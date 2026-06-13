const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

const repoRoot = path.resolve(__dirname, "..");

const ridersPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dispatch", "DispatchRidersPage.tsx"),
  "utf8"
);
const riderHelpers = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dispatch", "dispatchCenterLayout.helpers.ts"),
  "utf8"
);
const apiTypes = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "shared", "api", "types.ts"),
  "utf8"
);
const apiHttp = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "shared", "api", "http.ts"),
  "utf8"
);
const mobileAuth = fs.readFileSync(
  path.join(repoRoot, "backend", "src", "main", "java", "com", "jzqs", "app", "mobile", "MobileAuthServiceImpl.java"),
  "utf8"
);
const customerAssetService = fs.readFileSync(
  path.join(repoRoot, "backend", "src", "main", "java", "com", "jzqs", "app", "customer", "service", "impl", "CustomerAssetServiceImpl.java"),
  "utf8"
);
const bindRiderPhoneMethod = mobileAuth.match(
  /public Map<String, Object> bindRiderPhone\(String openid, String phone, String nickname\) \{[\s\S]*?\n    \}\n\n    @Override/
);

assert.ok(bindRiderPhoneMethod, "应能定位骑手手机号绑定方法");
const bindRiderPhoneSource = bindRiderPhoneMethod[0];

assert.doesNotMatch(ridersPage, /\bpassword\b/, "骑手管理页不应再保留密码字段或提交密码");
assert.doesNotMatch(ridersPage, /EyeOff|Eye/, "骑手管理页去掉密码后不应再保留显隐密码图标");
assert.match(ridersPage, /toast\(/, "骑手管理页校验失败和保存结果应给出 toast 提示");
assert.doesNotMatch(ridersPage, /window\.alert/, "骑手管理页不应再使用 window.alert 提示");
assert.match(ridersPage, /fieldErrors/, "骑手管理页应维护字段级校验错误状态");
assert.match(ridersPage, /form-error/, "骑手管理页应展示字段下方错误文案");
assert.match(ridersPage, /disabled=\{saving \|\| !canSubmit\}/, "骑手创建按钮应在校验失败时置灰");
assert.match(riderHelpers, /return \{[\s\S]*riderName:[\s\S]*phone:/, "骑手 helper 应返回结构化字段错误对象");

assert.doesNotMatch(riderHelpers, /\bpassword\b/, "骑手草稿和 payload helper 不应再生成默认密码");
assert.doesNotMatch(riderHelpers, /888888/, "骑手 helper 不应再内置默认密码 888888");

assert.doesNotMatch(
  apiTypes,
  /export type DispatchCreateRiderPayload = \{[\s\S]*password\?: string;[\s\S]*\}/,
  "骑手创建 payload 类型不应再声明 password 字段"
);
assert.doesNotMatch(
  apiHttp,
  /updateDispatchRiderProfile\(riderId: number, payload: \{[\s\S]*password\?: string;[\s\S]*\}\)/,
  "骑手编辑接口不应再允许前端传 password 字段"
);

assert.match(
  bindRiderPhoneSource,
  /throw new BusinessException\(ErrorCode\.CUSTOMER_NOT_FOUND, "该手机号未注册骑手账号"\);/,
  "骑手手机号绑定应要求后台已存在骑手档案"
);
assert.doesNotMatch(
  bindRiderPhoneSource,
  /INSERT INTO rider_profiles/,
  "骑手手机号绑定不应再自动创建骑手账号"
);

assert.match(customerAssetService, /WALLET_BALANCE_NOT_ENOUGH/, "客户手动扣餐时余额不足应抛出明确异常");
assert.doesNotMatch(
  customerAssetService,
  /int nextTotal = Math\.max\(0, nvl\(wallet\.getTotalMeals\(\)\) - request\.mealDelta\(\)\);/,
  "客户手动扣餐不应继续使用静默截断到 0 的旧逻辑"
);

console.log("PASS: 骑手登录去密码与客户余额防透支保护已锁定");
