import { describe, expect, it } from "vitest";
import { buildMenuWeekSummary, resolveWeekStatusLabel } from "./menuSchedulePage.helpers";
import type { AdminMenuWeekResponse } from "../../shared/api/types";

const week: AdminMenuWeekResponse = {
  weekId: 1,
  weekStartDate: "2026-05-18",
  weekEndDate: "2026-05-24",
  status: "DRAFT",
  days: [
    {
      serveDate: "2026-05-18",
      weekdayLabel: "周一",
      lunch: { mealPeriod: "LUNCH", slotStatus: "ACTIVE", dishItems: ["鱼香肉丝"], totalCalories: 520, merchantNote: "", imageUrl: "" },
      dinner: { mealPeriod: "DINNER", slotStatus: "ACTIVE", dishItems: ["番茄牛腩"], totalCalories: 560, merchantNote: "", imageUrl: "" }
    },
    {
      serveDate: "2026-05-19",
      weekdayLabel: "周二",
      lunch: { mealPeriod: "LUNCH", slotStatus: "REST", dishItems: [], totalCalories: null, merchantNote: "", imageUrl: "" },
      dinner: { mealPeriod: "DINNER", slotStatus: "REST", dishItems: [], totalCalories: null, merchantNote: "", imageUrl: "" }
    },
    {
      serveDate: "2026-05-20",
      weekdayLabel: "周三",
      lunch: { mealPeriod: "LUNCH", slotStatus: "ACTIVE", dishItems: ["宫保鸡丁"], totalCalories: 500, merchantNote: "", imageUrl: "" },
      dinner: { mealPeriod: "DINNER", slotStatus: "UNCONFIGURED", dishItems: [], totalCalories: null, merchantNote: "", imageUrl: "" }
    }
  ]
};

describe("buildMenuWeekSummary", () => {
  it("counts completed days separately from active slots", () => {
    expect(buildMenuWeekSummary(week)).toEqual({
      activeSlotCount: 3,
      configuredDayCount: 2,
      restDayCount: 1,
      unconfiguredSlotCount: 1,
      completionRate: "50%"
    });
  });
});

describe("resolveWeekStatusLabel", () => {
  it("maps week lifecycle statuses to chinese labels", () => {
    expect(resolveWeekStatusLabel("DRAFT")).toBe("待发布");
    expect(resolveWeekStatusLabel("PUBLISHED")).toBe("已发布");
    expect(resolveWeekStatusLabel("ARCHIVED")).toBe("已归档");
  });
});
