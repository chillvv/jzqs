import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

const checks = [
  {
    file: "src/modules/orders/OrderPrepPage.tsx",
    banned: ["今日运营动作建议", "优先处理清单", "顺手的运营链路", "建议优先确认写单", "先处理重点客户", "高频动作前置"]
  },
  {
    file: "src/modules/customers/CustomerAssetPage.tsx",
    banned: ["做成高频可操作后台", "建议优先跟进补餐或续卡", "最适合做召回", "优先续卡客户", "召回关注客户", "这里开放商家最常调整的客户档案字段"]
  },
  {
    file: "src/modules/analysis/OperationsAnalysisPage.tsx",
    banned: ["管理建议", "先看毛利率", "优先关注金额最高的几项支出"]
  },
  {
    file: "src/modules/menu/MenuSchedulePage.tsx",
    banned: ["建议优先补齐未配置日期", "当前进度提醒", "暂不建议直接发布", "可以进入发布检查阶段"]
  },
  {
    file: "src/modules/settings/SystemSettingsPage.tsx",
    banned: ["这里应该像一个运营控制台", "建议操作顺序", "优先走这里处理"]
  },
  {
    file: "src/modules/dashboard/DashboardPage.tsx",
    banned: ["先看今天的经营状态", "今日建议动作", "先盯履约，再盯续卡，再盯利润", "这个首页的职责不是展示静态数字"]
  }
];

for (const check of checks) {
  const filePath = path.join(root, check.file);
  const content = fs.readFileSync(filePath, "utf8");
  for (const bannedText of check.banned) {
    assert.equal(
      content.includes(bannedText),
      false,
      `${check.file} should not contain guide copy: ${bannedText}`
    );
  }
}

console.log("ui copy test: ok");
