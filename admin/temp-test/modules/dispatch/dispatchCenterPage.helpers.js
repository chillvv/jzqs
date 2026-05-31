export function createEmptyNewRiderDraft() {
  return {
    riderName: "",
    phone: "",
    password: "888888"
  };
}

export function buildDispatchWorkspaceNav() {
  return [
    { label: "配送工作台", value: "" },
    { label: "区域管理", value: "areas" },
    { label: "骑手管理", value: "riders" }
  ];
}

export function normalizeDispatchOverview(data) {
  return {
    pendingLunchCount: data.pendingLunchCount ?? 0,
    pendingDinnerCount: data.pendingDinnerCount ?? 0,
    pendingReminderCount: data.pendingReminderCount ?? 0,
    pendingExceptionCount: data.pendingExceptionCount ?? 0,
    activeBatches: Array.isArray(data.activeBatches) ? data.activeBatches : [],
    exceptions: Array.isArray(data.exceptions) ? data.exceptions : []
  };
}

export function buildDispatchHeroMetrics(overview) {
  return [
    { label: "午餐待配送", value: overview.pendingLunchCount, tone: "blue" },
    { label: "晚餐待配送", value: overview.pendingDinnerCount, tone: "violet" },
    { label: "已送待提醒", value: overview.pendingReminderCount, tone: "amber" },
    { label: "新地址待分配", value: overview.pendingExceptionCount, tone: "red" }
  ];
}

export function groupBatchesByMealPeriod(items) {
  return {
    lunch: items.filter((item) => item.mealPeriod === "LUNCH"),
    dinner: items.filter((item) => item.mealPeriod === "DINNER")
  };
}

export function getActiveQueueLabel(batch) {
  const current = batch.currentCustomerName || "待开始";
  const next = batch.nextCustomerName || "无";
  return `${current} / 下一单 ${next}`;
}

export function riderStatusLabel(status) {
  switch (status) {
    case "ACTIVE":
      return "启用中";
    case "DISABLED":
      return "已停用";
    default:
      return status || "未知";
  }
}

export function riderStatusTagClass(status) {
  switch (status) {
    case "ACTIVE":
      return "tag-green";
    case "DISABLED":
      return "tag-gray";
    default:
      return "tag-gray";
  }
}

export function validateCreateRiderDraft(draft) {
  if (!draft.riderName.trim()) {
    return "请填写骑手姓名";
  }
  if (!draft.phone.trim() || !/^1\d{10}$/.test(draft.phone.trim())) {
    return "请填写正确的手机号";
  }
  return null;
}

export function buildCreateRiderPayload(draft) {
  return {
    riderName: draft.riderName.trim(),
    displayName: draft.riderName.trim(),
    phone: draft.phone.trim(),
    password: draft.password.trim() || "888888",
    employmentStatus: "ACTIVE",
    updatedBy: "老板"
  };
}
