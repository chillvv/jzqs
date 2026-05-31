import assert from "node:assert/strict";
import {
  buildMenuWeekSummary,
  resolveWeekStatusLabel
} from "../temp-test/modules/menu/menuSchedulePage.helpers.js";

const week = {
  weekId: 1,
  weekStartDate: "2026-05-12",
  weekEndDate: "2026-05-18",
  status: "DRAFT",
  days: [
    {
      serveDate: "2026-05-12",
      weekdayLabel: "周一",
      lunch: { slotStatus: "ACTIVE", dishItems: ["鸡胸肉", "西兰花"], totalCalories: 420, merchantNote: "", imageUrl: "" },
      dinner: { slotStatus: "ACTIVE", dishItems: ["牛肉", "南瓜"], totalCalories: 460, merchantNote: "", imageUrl: "" }
    },
    {
      serveDate: "2026-05-13",
      weekdayLabel: "周二",
      lunch: { slotStatus: "UNCONFIGURED", dishItems: [], totalCalories: null, merchantNote: "", imageUrl: "" },
      dinner: { slotStatus: "REST", dishItems: [], totalCalories: null, merchantNote: "", imageUrl: "" }
    }
  ]
};

{
  const summary = buildMenuWeekSummary(week);
  assert.deepEqual(summary, {
    activeSlotCount: 2,
    restDayCount: 0,
    unconfiguredSlotCount: 1,
    completionRate: "50%"
  });
}

assert.equal(resolveWeekStatusLabel("DRAFT"), "待发布");
assert.equal(resolveWeekStatusLabel("PUBLISHED"), "已发布");
assert.equal(resolveWeekStatusLabel("ARCHIVED"), "已归档");

console.log("menu helpers test: ok");
