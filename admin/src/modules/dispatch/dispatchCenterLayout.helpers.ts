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
  enabled: boolean;
  /** 月薪（元），空字符串表示未设置；用于「骑手月度配送成本」统计的单均成本计算。 */
  monthlySalary: string;
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
    enabled: true,
    monthlySalary: ""
  };
}

export function buildDispatchWorkspaceNav() {
  return [
    { label: "分单工作台", value: "" },
    { label: "骑手进度", value: "progress" },
    { label: "区域管理", value: "areas" },
    { label: "骑手管理", value: "riders" },
    { label: "待释放送达", value: "release" },
    { label: "未通知名单", value: "notify-gaps" }
  ];
}

export function normalizeDispatchOverview(data: DispatchOverviewLike): DispatchOverviewResponse {
  return {
    pendingCount: data.pendingCount ?? 0,
    dispatchingCount: data.dispatchingCount ?? 0,
    missingRiderAreaCount: data.missingRiderAreaCount ?? 0,
    crossMealDeliveryOutCount: data.crossMealDeliveryOutCount ?? 0,
    crossMealDeliveryInCount: data.crossMealDeliveryInCount ?? 0
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
    phone: phoneError,
    monthlySalary: validateMonthlySalaryInput(draft.monthlySalary)
  };
}

export function validateAreaName(value: string) {
  if (!value.trim()) {
    return "请输入区域名称";
  }
  return "";
}

/** 月薪输入校验：留空表示未设置；填了必须是非负数字。 */
export function validateMonthlySalaryInput(value: string) {
  const trimmed = (value ?? "").trim();
  if (!trimmed) {
    return "";
  }
  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed)) {
    return "月薪需为数字";
  }
  if (parsed < 0) {
    return "月薪不能为负数";
  }
  if (parsed > 999999) {
    return "月薪数值过大";
  }
  return "";
}

/** 月薪输入（字符串）转接口入参：空串 → null，表示「未设置月薪」。 */
export function parseMonthlySalaryInput(value: string): number | null {
  const trimmed = (value ?? "").trim();
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

/** 月薪展示：未设置或 0 显示为「未设置」。 */
export function formatMonthlySalary(value: number | null | undefined) {
  if (value == null || Number(value) <= 0) {
    return "未设置";
  }
  return `¥ ${Number(value).toFixed(2)}`;
}

/** 单均人工成本展示：后端未给出（无月薪 / 当月无单）时显示占位符。 */
export function formatCostPerOrder(value: number | null | undefined) {
  if (value == null) {
    return "--";
  }
  return `¥ ${Number(value).toFixed(2)}`;
}

/** 金额展示（保留 2 位小数），用于月度成本合计等数值。 */
export function formatMoneyAmount(value: number | null | undefined) {
  const parsed = Number(value);
  if (value == null || !Number.isFinite(parsed)) {
    return "0.00";
  }
  return parsed.toFixed(2);
}

/** 占比数值裁剪到 0-100，避免脏数据把占比条撑破。 */
export function clampPercent(value: number | null | undefined) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return 0;
  }
  return Math.min(Math.max(parsed, 0), 100);
}

export type RiderCostTone = "none" | "low" | "mid" | "high";

/**
 * 以「整体单均成本」为基准给单个骑手的单均成本分档：
 * 明显低于基准 = 省（low），接近基准 = 正常（mid），明显高于基准 = 偏贵（high）；
 * 骑手没有单均成本（未设月薪或当月无单）时为 none。
 */
export function resolveRiderCostTone(
  costPerOrder: number | null | undefined,
  baseline: number | null | undefined
): RiderCostTone {
  if (costPerOrder == null) {
    return "none";
  }
  const safeBaseline = Number(baseline);
  if (!Number.isFinite(safeBaseline) || safeBaseline <= 0) {
    return "mid";
  }
  if (costPerOrder < safeBaseline * 0.85) {
    return "low";
  }
  if (costPerOrder > safeBaseline * 1.15) {
    return "high";
  }
  return "mid";
}

export function buildCreateRiderPayload(draft: NewRiderDraft) {
  return {
    riderName: draft.riderName.trim(),
    displayName: draft.riderName.trim(),
    phone: draft.phone.trim(),
    employmentStatus: draft.enabled ? "ACTIVE" : "DISABLED",
    monthlySalary: parseMonthlySalaryInput(draft.monthlySalary)
  };
}
