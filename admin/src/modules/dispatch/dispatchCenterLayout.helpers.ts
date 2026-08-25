import type {
  DispatchAreaBindingResponse,
  DispatchAreaOrderItemResponse,
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
  lunchEnabled: boolean;
  lunchAreaCode: string;
  dinnerEnabled: boolean;
  dinnerAreaCode: string;
};

/** 人员维度的骑手：同一骑手在午餐/晚餐可能各有一条档案记录，状态、归属区域彼此独立 */
export type MergedRider = {
  riderName: string;
  phone: string;
  lunch: DispatchManagedRiderResponse | null;
  dinner: DispatchManagedRiderResponse | null;
};

export const DEFAULT_OPERATOR = "管理员";

export type DispatchMealPeriod = "LUNCH" | "DINNER";

export const MEAL_PERIOD_OPTIONS: Array<{ label: string; value: DispatchMealPeriod }> = [
  { label: "午餐", value: "LUNCH" },
  { label: "晚餐", value: "DINNER" }
];
type DispatchAreaBindingLike = Omit<DispatchAreaBindingResponse, "orders"> & {
  orders?: DispatchAreaBindingResponse["orders"] | null;
};

export function createEmptyNewRiderDraft(): NewRiderDraft {
  return {
    riderName: "",
    phone: "",
    lunchEnabled: true,
    lunchAreaCode: "",
    dinnerEnabled: true,
    dinnerAreaCode: ""
  };
}

/** 将午餐、晚餐两批骑手档案按 riderName 合并为"人员"列表，一人一行 */
export function mergeRidersByMealPeriod(
  lunchRiders: DispatchManagedRiderResponse[],
  dinnerRiders: DispatchManagedRiderResponse[]
): MergedRider[] {
  const map = new Map<string, MergedRider>();
  const push = (rider: DispatchManagedRiderResponse) => {
    const key = rider.riderName;
    const existing = map.get(key);
    if (existing) {
      if (rider.mealPeriod === "LUNCH") {
        existing.lunch = rider;
      } else {
        existing.dinner = rider;
      }
      if (!existing.phone && rider.phone) {
        existing.phone = rider.phone;
      }
    } else {
      map.set(key, {
        riderName: key,
        phone: rider.phone || "",
        lunch: rider.mealPeriod === "LUNCH" ? rider : null,
        dinner: rider.mealPeriod === "DINNER" ? rider : null
      });
    }
  };
  lunchRiders.forEach(push);
  dinnerRiders.forEach(push);
  return Array.from(map.values());
}

export function buildDispatchWorkspaceNav() {
  return [
    { label: "分单工作台", value: "" },
    { label: "骑手进度", value: "progress" },
    { label: "区域管理", value: "areas" },
    { label: "骑手管理", value: "riders" },
    { label: "待释放送达", value: "release" }
  ];
}

export function normalizeDispatchOverview(data: DispatchOverviewLike): DispatchOverviewResponse {
  return {
    pendingCount: data.pendingCount ?? 0,
    dispatchingCount: data.dispatchingCount ?? 0,
    missingRiderAreaCount: data.missingRiderAreaCount ?? 0
  };
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
  let totalOrderCount = 0;
  let dispatchingCount = 0;
  for (const area of bindings) {
    for (const item of area.orders) {
      const qty = item.quantity || 1;
      totalOrderCount += qty;
      if (item.deliveryStatus === "PENDING_DISPATCH") {
        dispatchingCount += qty;
      }
    }
  }
  return {
    totalCount: bindings.length,
    totalOrderCount,
    dispatchingCount,
    missingRiderAreaCount: bindings.filter((area) => area.missingRider).length
  };
}

export function reorderDispatchAreaOrders(
  orders: DispatchAreaOrderItemResponse[],
  finalOrderIds: number[]
) {
  if (!orders.length || !finalOrderIds.length) {
    return orders;
  }

  const orderMap = new Map<number, DispatchAreaOrderItemResponse>();
  orders.forEach((item) => orderMap.set(item.orderId, item));

  const reordered = finalOrderIds
    .map((orderId) => orderMap.get(orderId))
    .filter((item): item is DispatchAreaOrderItemResponse => Boolean(item));

  return reordered.length === orders.length ? reordered : orders;
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
  const riderName = draft.riderName.trim();
  const phone = draft.phone.trim();
  const riderNameError = !riderName
    ? "请填写骑手姓名"
    : riderName.length < 2 || riderName.length > 20
      ? "姓名需为2-20字"
      : !/^[\u4e00-\u9fa5A-Za-z0-9·\s]+$/.test(riderName)
        ? "姓名仅支持中文、字母、数字和间隔号"
      : "";
  const phoneError = !phone
    ? "请填写手机号"
    : !/^1\d{10}$/.test(phone)
      ? "手机号少了一位哦，请输入 11 位手机号"
      : "";

  return {
    riderName: riderNameError,
    phone: phoneError
  };
}

export function validateAreaName(value: string) {
  if (!value.trim()) {
    return "请输入区域名称";
  }
  return "";
}

export function buildCreateRiderPayload(draft: NewRiderDraft, mealPeriod: DispatchMealPeriod) {
  const enabled = mealPeriod === "LUNCH" ? draft.lunchEnabled : draft.dinnerEnabled;
  const areaCode = mealPeriod === "LUNCH" ? draft.lunchAreaCode : draft.dinnerAreaCode;
  return {
    mealPeriod,
    riderName: draft.riderName.trim(),
    displayName: draft.riderName.trim(),
    phone: draft.phone.trim(),
    areaCode: enabled ? (areaCode.trim() || undefined) : undefined,
    employmentStatus: enabled ? "ACTIVE" : "DISABLED"
  };
}
