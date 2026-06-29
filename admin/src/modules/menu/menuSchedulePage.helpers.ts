import type { AdminMenuWeekResponse } from "../../shared/api/types";

export function buildMenuWeekSummary(week: AdminMenuWeekResponse) {
  const slots = week.days.flatMap((day) => [day.lunch, day.dinner]);
  const activeSlotCount = slots.filter((slot) => slot.slotStatus === "ACTIVE").length;
  const unconfiguredSlotCount = slots.filter((slot) => slot.slotStatus === "UNCONFIGURED").length;
  const restDayCount = week.days.filter((day) => day.lunch.slotStatus === "REST" && day.dinner.slotStatus === "REST").length;
  const configuredDayCount = week.days.filter((day) => day.lunch.slotStatus !== "UNCONFIGURED" && day.dinner.slotStatus !== "UNCONFIGURED").length;

  return {
    activeSlotCount,
    configuredDayCount,
    restDayCount,
    unconfiguredSlotCount,
    completionRate: `${Math.round((activeSlotCount / Math.max(slots.length, 1)) * 100)}%`
  };
}

export function resolveWeekStatusLabel(status: string) {
  if (status === "DRAFT") return "待发布";
  if (status === "PUBLISHED") return "已发布";
  if (status === "ARCHIVED") return "已归档";
  return status || "-";
}
