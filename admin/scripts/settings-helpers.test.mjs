import assert from "node:assert/strict";
import {
  buildOperationRiskSummary,
  resolveOrderingTone
} from "../temp-test/modules/settings/systemSettingsPage.helpers.js";

{
  const result = buildOperationRiskSummary({
    orderingEnabled: true,
    orderingStatusLabel: "通道开启中",
    holidayNoticeTitle: "五一店休公告",
    holidayNoticeDesc: "5月1日至5月3日暂停接单",
    emergencyActionLabel: "熔断：一键暂停接单"
  });

  assert.deepEqual(result, {
    primaryHint: "当前仍可接单，若实际休息请先关停通道。",
    secondaryHint: "公告已配置，记得核对恢复营业时间和小程序展示文案。",
    tone: "warning"
  });
}

assert.equal(resolveOrderingTone(true), "green");
assert.equal(resolveOrderingTone(false), "gray");

console.log("settings helpers test: ok");
