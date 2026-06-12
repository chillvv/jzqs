import type {
  DispatchAreaBindingResponse,
  DispatchBatchResponse,
  DispatchManagedRiderResponse,
  DispatchOverviewResponse,
  DispatchPendingItemResponse,
  DispatchRiderProgressResponse
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
    { label: "分单工作台", value: "" },
    { label: "骑手进度", value: "progress" },
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

export function hasDisplayValue(value: string | null | undefined) {
  return Boolean(value && value.trim() && value.trim() !== "-");
}

export function hasOrderAttention(order: { userNote?: string | null; merchantRemark?: string | null }) {
  return hasDisplayValue(order.userNote) || hasDisplayValue(order.merchantRemark);
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

export type DispatchBoardSelection = {
  selectedRiderName?: string;
  selectedOrderId?: number;
};

export type DispatchBoardRiderCard = DispatchRiderProgressResponse & {
  key: string;
};

export type DispatchBoardViewModel = {
  riderCards: DispatchBoardRiderCard[];
  activeRider: DispatchBoardRiderCard | null;
  activeBinding: DispatchAreaBindingResponse | null;
  queueOrders: DispatchAreaBindingResponse["orders"];
  activeOrder: DispatchAreaBindingResponse["orders"][number] | null;
};

export function buildDispatchBoardViewModel(
  riderProgress: DispatchRiderProgressResponse[],
  areaBindings: DispatchAreaBindingResponse[],
  selection: DispatchBoardSelection = {}
): DispatchBoardViewModel {
  const riderCards = riderProgress.map((item) => ({
    ...item,
    key: `${item.riderName}@${item.areaCode}`
  }));
  const activeRider =
    riderCards.find((item) => item.riderName === selection.selectedRiderName) ??
    riderCards[0] ??
    null;

  const activeBinding =
    areaBindings.find((item) => item.areaCode === activeRider?.areaCode) ??
    areaBindings.find((item) => item.currentRiderName === activeRider?.riderName) ??
    areaBindings.find((item) => item.defaultRiderName === activeRider?.riderName) ??
    null;

  const queueOrders = [...(activeBinding?.orders ?? [])].sort((left, right) => left.sequenceNumber - right.sequenceNumber);
  const activeOrder =
    queueOrders.find((item) => item.orderId === selection.selectedOrderId) ??
    queueOrders.find((item) => item.orderId === activeRider?.currentOrderId) ??
    queueOrders[0] ??
    null;

  return {
    riderCards,
    activeRider,
    activeBinding,
    queueOrders,
    activeOrder
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
