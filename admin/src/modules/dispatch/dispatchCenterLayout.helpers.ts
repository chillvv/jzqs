import type {
  DispatchAreaBindingResponse,
  DispatchBatchResponse,
  DispatchManagedRiderResponse,
  DispatchOverviewResponse,
  DispatchPendingItemResponse
} from "../../shared/api/types";

type DispatchOverviewLike = Partial<DispatchOverviewResponse>;

export type NewRiderDraft = {
  riderName: string;
  phone: string;
  password: string;
  areaCode: string;
};

export const DEFAULT_OPERATOR = "管理员";

export type DispatchMealPeriod = "LUNCH" | "DINNER";
type DispatchAreaBindingLike = Omit<DispatchAreaBindingResponse, "orders"> & {
  orders?: DispatchAreaBindingResponse["orders"] | null;
};

export function createEmptyNewRiderDraft(): NewRiderDraft {
  return {
    riderName: "",
    phone: "",
    password: "888888",
    areaCode: ""
  };
}

export function buildDispatchWorkspaceNav() {
  return [
    { label: "配送工作台", value: "" },
    { label: "区域管理", value: "areas" },
    { label: "骑手管理", value: "riders" }
  ];
}

export function normalizeDispatchOverview(data: DispatchOverviewLike): DispatchOverviewResponse {
  return {
    pendingCount: data.pendingCount ?? 0,
    dispatchingCount: data.dispatchingCount ?? 0,
    missingRiderAreaCount: data.missingRiderAreaCount ?? 0
  };
}

export function normalizeMealPeriodTab(value: string): DispatchMealPeriod {
  return value === "DINNER" || value === "晚餐" ? "DINNER" : "LUNCH";
}

export function mealPeriodLabel(value: DispatchMealPeriod) {
  return value === "DINNER" ? "晚餐" : "午餐";
}

export function buildDispatchPendingSearchText(item: DispatchPendingItemResponse) {
  return `${item.orderId} ${item.customerName} ${item.deliveryAddress}`.toLowerCase();
}

export function normalizeDispatchAreaBindings(items: DispatchAreaBindingLike[]) {
  return items.map((item) => {
    const orders = Array.isArray(item.orders) ? item.orders : [];
    // 移除前端去重逻辑，确保后端返回多少条就显示多少条，以暴露真实数据问题
    return { ...item, orders };
  });
}

export function buildDispatchAreaStats(bindings: DispatchAreaBindingResponse[]) {
  return {
    totalCount: bindings.length,
    dispatchingCount: bindings.reduce(
      (sum, area) => sum + area.orders.filter((item) => item.deliveryStatus === "PENDING_DISPATCH").length,
      0
    ),
    missingRiderAreaCount: bindings.filter((area) => area.missingRider).length
  };
}

export function groupBatchesByMealPeriod(items: DispatchBatchResponse[]) {
  return {
    lunch: items.filter((item) => item.mealPeriod === "LUNCH"),
    dinner: items.filter((item) => item.mealPeriod === "DINNER")
  };
}

export function getActiveQueueLabel(batch: DispatchBatchResponse) {
  const current = batch.currentCustomerName || "待开始";
  const next = batch.nextCustomerName || "无";
  return `${current} / 下一单 ${next}`;
}

export function riderStatusLabel(status: string) {
  switch (status) {
    case "ACTIVE":
      return "启用中";
    case "DISABLED":
      return "已停用";
    default:
      return status || "未知";
  }
}

export function riderStatusTagClass(status: string) {
  switch (status) {
    case "ACTIVE":
      return "tag-green";
    case "DISABLED":
      return "tag-gray";
    default:
      return "tag-gray";
  }
}

export function validateCreateRiderDraft(draft: NewRiderDraft) {
  if (!draft.riderName.trim()) {
    return "请填写骑手姓名";
  }
  if (!draft.phone.trim() || !/^1\d{10}$/.test(draft.phone.trim())) {
    return "请填写正确的手机号";
  }
  return null;
}

export function buildCreateRiderPayload(draft: NewRiderDraft) {
  return {
    riderName: draft.riderName.trim(),
    displayName: draft.riderName.trim(),
    phone: draft.phone.trim(),
    password: draft.password.trim() || "888888",
    areaCode: draft.areaCode.trim() || undefined,
    employmentStatus: "ACTIVE",
    updatedBy: DEFAULT_OPERATOR
  };
}
