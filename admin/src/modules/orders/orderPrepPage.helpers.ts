import type { OrderPrepItemResponse, SubscriptionConfirmationItem } from "../../shared/api/types";

export type OrderPrepMealPeriodFilter = "LUNCH" | "DINNER";
export type OrderPrepSourceFilter = "ALL" | "MINIAPP" | "BACKEND" | "SUBSCRIPTION";
export type OrderPrepRemarkFilter = "ALL" | "HAS_REMARK" | "NO_REMARK";
export type OrderPrepStatusFilter =
  | "ALL"
  | "PENDING_DISPATCH"
  | "DISPATCHING"
  | "DELIVERED"
  | "REFUND_PROCESSING"
  | "REFUNDED"
  | "CANCELLED";

export type OrderPrepFilters = {
  keyword: string;
  mealPeriod: OrderPrepMealPeriodFilter;
  source: OrderPrepSourceFilter;
  status: OrderPrepStatusFilter;
  remark: OrderPrepRemarkFilter;
};

export type OrderPrepTab = "CONFIRMATION" | "ORDERS" | "SUBSCRIPTION_MANAGEMENT";

/**
 * 这些状态的订单属于"已处理完毕/已取消/已退款"，对运营出餐无意义，
 * 不计入今日订单中心的列表展示与份数统计（秒退款、已取消、已退款均不显示）。
 */
export const ORDER_STATUSES_EXCLUDED_FROM_TODAY = ["CANCELLED", "REFUNDED"];

/** 退款类订单需要运营主动处理，在列表中置顶展示，方便优先跟进。 */
export const ORDER_STATUSES_PINNED_TO_TOP = ["REFUND_PROCESSING"];

export function isOrderExcludedFromToday(item: OrderPrepItemResponse) {
  return ORDER_STATUSES_EXCLUDED_FROM_TODAY.includes(resolveOrderDisplayStatus(item));
}

export function isOrderPinnedToTop(item: OrderPrepItemResponse) {
  return ORDER_STATUSES_PINNED_TO_TOP.includes(resolveOrderDisplayStatus(item));
}

export type OrderPrepCompactSummaryItem = {
  label: string;
  value: string;
  tone: "blue" | "orange" | "red" | "green";
  /** 关联下方"查看餐次"切换的餐次，存在则该卡片可点击并随选中态高亮 */
  mealPeriod?: "LUNCH" | "DINNER";
};

export type OrderRemarkLabelItem = {
  orderId: number;
  customerName: string;
  customerPhone: string;
  deliveryAddress: string;
  remarkLine: string;
};

export function formatOrderNote(value: string | null | undefined) {
  const trimmed = value?.trim() ?? "";
  return trimmed || "-";
}

export function mealPeriodLabel(value: string | null | undefined) {
  return value === "DINNER" ? "晚餐" : "午餐";
}

export function isMeaningfulRemark(value: string | null | undefined) {
  const trimmed = value?.trim() ?? "";
  return trimmed.length > 0 && trimmed !== "-";
}

export function isCrossMealDelivery(
  mealPeriod: string | null | undefined,
  deliveryMealPeriod: string | null | undefined
) {
  const normalizedMealPeriod = mealPeriod === "DINNER" ? "DINNER" : "LUNCH";
  const normalizedDeliveryMealPeriod = deliveryMealPeriod === "DINNER" ? "DINNER" : "LUNCH";
  return normalizedMealPeriod !== normalizedDeliveryMealPeriod;
}

export function buildCrossMealDeliveryRemark(
  merchantRemark: string | null | undefined,
  mealPeriod: string | null | undefined,
  deliveryMealPeriod: string | null | undefined
) {
  const trimmedRemark = merchantRemark?.trim() ?? "";
  if (!isCrossMealDelivery(mealPeriod, deliveryMealPeriod)) {
    return trimmedRemark;
  }
  const deliveryRemark = `${mealPeriodLabel(mealPeriod)}，${mealPeriodLabel(deliveryMealPeriod)}配送`;
  if (!trimmedRemark) {
    return deliveryRemark;
  }
  if (trimmedRemark.includes(deliveryRemark)) {
    return trimmedRemark;
  }
  return `${deliveryRemark}；${trimmedRemark}`;
}

export function resolveMealPeriod(item: OrderPrepItemResponse): OrderPrepMealPeriodFilter {
  if (item.mealPeriod === "DINNER" || item.mealPeriod === "LUNCH") {
    return item.mealPeriod;
  }
  return item.mealSummary.includes("晚餐") ? "DINNER" : "LUNCH";
}

export function resolveOrderSourceLabel(item: OrderPrepItemResponse) {
  if (item.fixedSubscription) {
    return "固定订餐";
  }

  if (item.source === "BACKEND") {
    return "后台录入";
  }

  return "小程序";
}

export function resolveOrderDisplayStatus(item: Pick<OrderPrepItemResponse, "status"> & Partial<Pick<OrderPrepItemResponse, "displayStatus">>) {
  return item.displayStatus || item.status;
}

export function resolveOrderDisplayStatusLabel(status: string) {
  if (status === "REFUND_PROCESSING") {
    return "退款处理中";
  }
  if (status === "DELIVERED") {
    return "已完成";
  }
  if (status === "DISPATCHING") {
    return "配送中";
  }
  if (status === "REFUNDED") {
    return "已退款";
  }
  if (status === "CANCELLED") {
    return "已取消";
  }
  return "待配送";
}

/**
 * 状态配色与顾客端保持同一套语义：
 * 待配送=橙、配送中=蓝、已送达=绿、退款相关=红（需要注意）、已取消=灰（弱化）。
 */
export function resolveOrderStatusTone(status: string): "orange" | "blue" | "green" | "red" | "gray" {
  if (status === "CANCELLED") {
    return "gray";
  }
  if (status === "REFUND_PROCESSING" || status === "REFUNDED") {
    return "red";
  }
  if (status === "DISPATCHING") {
    return "blue";
  }
  if (status === "DELIVERED") {
    return "green";
  }
  return "orange";
}

export function buildMealPrepExportRows(items: OrderPrepItemResponse[]) {
  return items.map((item) => {
    const displayStatus = resolveOrderDisplayStatus(item);
    return {
      "订单ID": item.id,
      "客户姓名": item.customerName,
      "联系电话": item.customerPhone,
      "餐次": item.mealSummary,
      "数量": item.quantity,
      "配送地址": item.deliveryAddress,
      "订单来源": resolveOrderSourceLabel(item),
      "用户备注": formatOrderNote(item.userNote),
      "商家备注": formatOrderNote(item.merchantRemark),
      "订单状态": item.displayStatusLabel || resolveOrderDisplayStatusLabel(displayStatus)
    };
  });
}

export function buildOrderRemarkLine(userNote: string | null | undefined, merchantRemark: string | null | undefined) {
  const segments: string[] = [];
  if (isMeaningfulRemark(userNote)) {
    segments.push(`用户备注：${userNote!.trim()}`);
  }
  if (isMeaningfulRemark(merchantRemark)) {
    segments.push(`商家备注：${merchantRemark!.trim()}`);
  }
  return segments.join("；") || "-";
}

export function buildOrderRemarkLabelItems(items: OrderPrepItemResponse[]): OrderRemarkLabelItem[] {
  return items
    .filter((item) => isMeaningfulRemark(item.userNote) || isMeaningfulRemark(item.merchantRemark))
    .map((item) => ({
      orderId: item.id,
      customerName: item.customerName.trim(),
      customerPhone: item.customerPhone.trim(),
      deliveryAddress: item.deliveryAddress?.trim() || "-",
      remarkLine: buildOrderRemarkLine(item.userNote, item.merchantRemark)
    }));
}

export function buildRemarkLabelText(item: OrderRemarkLabelItem) {
  return [item.customerName, item.customerPhone, item.deliveryAddress, item.remarkLine].join("\n");
}

export function buildRemarkLabelBatchText(items: OrderRemarkLabelItem[]) {
  return items.map((item) => buildRemarkLabelText(item)).join("\n\n");
}

export function buildOrderPrepView(
  items: OrderPrepItemResponse[],
  filters: OrderPrepFilters,
  currentPage: number,
  pageSize: number
) {
  const keyword = filters.keyword.trim();
  // 先剔除已取消/已退款订单（秒退款等无用信息不进入今日订单中心）
  const visibleItems = items.filter((item) => !isOrderExcludedFromToday(item));
  const filteredItems = visibleItems.filter((item) => {
    const matchesKeyword = keyword.length === 0
      || item.customerName.includes(keyword)
      || item.customerPhone.includes(keyword)
      || item.mealSummary.includes(keyword)
      || formatOrderNote(item.userNote).includes(keyword)
      || formatOrderNote(item.merchantRemark).includes(keyword);

    const matchesMealPeriod = resolveMealPeriod(item) === filters.mealPeriod;

    const sourceLabel = resolveOrderSourceLabel(item);
    const matchesSource = filters.source === "ALL"
      || (filters.source === "MINIAPP" && sourceLabel === "小程序")
      || (filters.source === "BACKEND" && sourceLabel === "后台录入")
      || (filters.source === "SUBSCRIPTION" && sourceLabel === "固定订餐");

    const matchesStatus = filters.status === "ALL" || resolveOrderDisplayStatus(item) === filters.status;
    const hasRemark = isMeaningfulRemark(item.userNote) || isMeaningfulRemark(item.merchantRemark);
    const matchesRemark = filters.remark === "ALL"
      || (filters.remark === "HAS_REMARK" && hasRemark)
      || (filters.remark === "NO_REMARK" && !hasRemark);

    return matchesKeyword && matchesMealPeriod && matchesSource && matchesStatus && matchesRemark;
  });

  // 退款处理中等需要运营跟进的订单置顶，其余保持原有顺序
  const pinnedItems = filteredItems.filter((item) => isOrderPinnedToTop(item));
  const otherItems = filteredItems.filter((item) => !isOrderPinnedToTop(item));
  const sortedItems = [...pinnedItems, ...otherItems];

  const totalItems = sortedItems.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const safeCurrentPage = Math.min(Math.max(currentPage, 1), totalPages);
  const startIndex = (safeCurrentPage - 1) * pageSize;
  const pageItems = sortedItems.slice(startIndex, startIndex + pageSize);

  return {
    filteredItems,
    pageItems,
    totalItems,
    totalPages,
    currentPage: safeCurrentPage
  };
}

export function buildOrderPrepSummary(
  items: OrderPrepItemResponse[],
  confirmationItems: SubscriptionConfirmationItem[]
) {
  // 已取消/已退款（含秒退款）不计入份数与订单统计
  const countedItems = items.filter((item) => !isOrderExcludedFromToday(item));

  const totalMeals = countedItems.reduce((sum, item) => sum + item.quantity, 0);
  const lunchCount = countedItems
    .filter(item => resolveMealPeriod(item) === "LUNCH")
    .reduce((sum, item) => sum + item.quantity, 0);
  const dinnerCount = countedItems
    .filter(item => resolveMealPeriod(item) === "DINNER")
    .reduce((sum, item) => sum + item.quantity, 0);

  const lunchRemarkedCount = countedItems
    .filter((item) => resolveMealPeriod(item) === "LUNCH" && hasRemark(item))
    .length;
  const dinnerRemarkedCount = countedItems
    .filter((item) => resolveMealPeriod(item) === "DINNER" && hasRemark(item))
    .length;

  // 待确认固定订餐按午餐/晚餐分别统计份数
  const lunchConfirmationCount = confirmationItems
    .filter((item) => item.mealPeriod === "LUNCH")
    .reduce((sum, item) => sum + (item.quantity ?? 1), 0);
  const dinnerConfirmationCount = confirmationItems
    .filter((item) => item.mealPeriod === "DINNER")
    .reduce((sum, item) => sum + (item.quantity ?? 1), 0);

  return {
    totalOrders: countedItems.length,
    totalMeals,
    lunchCount,
    dinnerCount,
    pendingDispatchCount: countedItems.filter((item) => item.status === "PENDING_DISPATCH").length,
    remarkedOrderCount: countedItems.filter((item) => hasRemark(item)).length,
    lunchRemarkedCount,
    dinnerRemarkedCount,
    confirmationCount: confirmationItems.length,
    lunchConfirmationCount,
    dinnerConfirmationCount
  };
}

export function buildOrderPrepCompactSummary(
  stats: Pick<
    import("../../shared/api/types").OrderPrepStatsResponse,
    "totalMeals" | "lunchCount" | "dinnerCount"
  >,
  summary: Pick<ReturnType<typeof buildOrderPrepSummary>, "confirmationCount" | "totalMeals" | "lunchCount" | "dinnerCount" | "lunchConfirmationCount" | "dinnerConfirmationCount">
) : OrderPrepCompactSummaryItem[] {
  return [
    {
      label: "当前待出餐",
      value: `${summary.totalMeals} 份`,
      tone: "blue"
    },
    {
      label: "午餐待确认",
      value: `${summary.lunchConfirmationCount} 份`,
      tone: "orange",
      mealPeriod: "LUNCH"
    },
    {
      label: "晚餐待确认",
      value: `${summary.dinnerConfirmationCount} 份`,
      tone: "green",
      mealPeriod: "DINNER"
    },
    {
      label: "待确认固定订餐",
      value: `${summary.confirmationCount} 份`,
      tone: "red"
    }
  ];
}

export function buildOrderPrepDefaultTab(confirmationCount: number): OrderPrepTab {
  return confirmationCount > 0 ? "CONFIRMATION" : "ORDERS";
}

export function buildSubscriptionConfirmationPanelState(confirmationCount: number) {
  return {
    visible: confirmationCount > 0,
    expanded: confirmationCount > 0
  };
}

function hasRemark(item: OrderPrepItemResponse) {
  return isMeaningfulRemark(item.userNote) || isMeaningfulRemark(item.merchantRemark);
}
