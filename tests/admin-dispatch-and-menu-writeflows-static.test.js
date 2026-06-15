const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

const repoRoot = path.resolve(__dirname, "..");
const dispatchAreasPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dispatch", "DispatchAreasPage.tsx"),
  "utf8"
);
const dispatchRidersPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "dispatch", "DispatchRidersPage.tsx"),
  "utf8"
);
const menuPage = fs.readFileSync(
  path.join(repoRoot, "admin", "src", "modules", "menu", "MenuSchedulePage.tsx"),
  "utf8"
);

assert.match(
  dispatchAreasPage,
  /async function handleCreateArea\(\)[\s\S]*updateDispatchAreaBinding\(newArea\.name\.trim\(\), \{[\s\S]*keywords:\s*newArea\.name\.trim\(\)[\s\S]*defaultRiderId:\s*newArea\.riderId \? Number\(newArea\.riderId\) : null[\s\S]*updatedBy:\s*DEFAULT_OPERATOR[\s\S]*toast\("区域已创建"\)/,
  "区域管理页应将新增区域弹窗提交到 area binding 接口，并在成功后给出提示"
);

assert.match(
  dispatchAreasPage,
  /onClick=\{handleCreateArea\}/,
  "新增区域确认按钮应绑定 handleCreateArea"
);

assert.match(
  dispatchRidersPage,
  /async function handleSave\(\)[\s\S]*if \(editRiderId\) \{[\s\S]*updateDispatchRiderProfile\(editRiderId, payload\)[\s\S]*\} else \{[\s\S]*createDispatchRider\(buildCreateRiderPayload\(draft\)\)/,
  "骑手页保存逻辑应区分编辑与新增，并分别调用更新/创建接口"
);

assert.match(
  dispatchRidersPage,
  /const payload = \{[\s\S]*riderName:\s*draft\.riderName\.trim\(\)[\s\S]*displayName:\s*draft\.riderName\.trim\(\)[\s\S]*phone:\s*draft\.phone\.trim\(\)[\s\S]*areaCode:\s*draft\.areaCode\.trim\(\)[\s\S]*updatedBy:\s*DEFAULT_OPERATOR[\s\S]*\}/,
  "编辑骑手时应使用修整后的表单字段构造更新 payload"
);

assert.match(
  dispatchRidersPage,
  /onClick=\{handleSave\}/,
  "骑手弹窗确认按钮应绑定 handleSave"
);

assert.match(
  menuPage,
  /async function handleSaveDay\(serveDate: string\)[\s\S]*await saveMenuWeekDay\(week\.weekId, serveDate, drafts\[serveDate\]\)[\s\S]*toast\("保存成功", "success"\)/,
  "周菜单页保存当天应调用 saveMenuWeekDay 并在成功后提示"
);

assert.match(
  menuPage,
  /async function handlePublish\(\)[\s\S]*await publishMenuWeek\(week\.weekId\)[\s\S]*toast\(`\$\{week\.weekStartDate\} ~ \$\{week\.weekEndDate\} 菜单已发布`, "success"\)/,
  "周菜单页发布动作应调用 publishMenuWeek 并在成功后提示"
);

assert.match(
  menuPage,
  /onClick=\{\(\) => handleSaveDay\(day\.serveDate\)\.catch/,
  "周菜单页保存按钮应绑定 handleSaveDay"
);

assert.match(
  menuPage,
  /onClick=\{\(\) => handlePublish\(\)\.catch/,
  "周菜单页发布确认按钮应绑定 handlePublish"
);

console.log("PASS: 调度与菜单核心写操作前端链路已锁定");
