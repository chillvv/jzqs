export function buildMenuWeekSummary(week) {
    const slots = week.days.flatMap((day) => [day.lunch, day.dinner]);
    const activeSlotCount = slots.filter((slot) => slot.slotStatus === "ACTIVE").length;
    const unconfiguredSlotCount = slots.filter((slot) => slot.slotStatus === "UNCONFIGURED").length;
    const restDayCount = week.days.filter((day) => day.lunch.slotStatus === "REST" && day.dinner.slotStatus === "REST").length;
    return {
        activeSlotCount,
        restDayCount,
        unconfiguredSlotCount,
        completionRate: `${Math.round((activeSlotCount / Math.max(slots.length, 1)) * 100)}%`
    };
}
export function resolveWeekStatusLabel(status) {
    if (status === "DRAFT")
        return "待发布";
    if (status === "PUBLISHED")
        return "已发布";
    if (status === "ARCHIVED")
        return "已归档";
    return status || "-";
}
