import type { OrderPrepItemResponse } from "../../shared/api/types";

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

export type OrderPrepCompactSummaryItem = {
  label: string;
  value: string;
  tone: "blue" | "orange" | "red";
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

export function resolveOrderStatusTone(status: string): "orange" | "blue" | "green" | "red" {
  if (status === "REFUND_PROCESSING" || status === "REFUNDED" || status === "CANCELLED") {
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

export function buildOrderPrepView(
  items: OrderPrepItemResponse[],
  filters: OrderPrepFilters,
  currentPage: number,
  pageSize: number
) {
  const keyword = filters.keyword.trim();
  const filteredItems = items.filter((item) => {
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

  const totalItems = filteredItems.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const safeCurrentPage = Math.min(Math.max(currentPage, 1), totalPages);
  const startIndex = (safeCurrentPage - 1) * pageSize;
  const pageItems = filteredItems.slice(startIndex, startIndex + pageSize);

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
  confirmationItems: Array<{ priority?: boolean }>
) {
  const totalMeals = items.reduce((sum, item) => sum + item.quantity, 0);
  const lunchCount = items
    .filter(item => resolveMealPeriod(item) === "LUNCH")
    .reduce((sum, item) => sum + item.quantity, 0);
  const dinnerCount = items
    .filter(item => resolveMealPeriod(item) === "DINNER")
    .reduce((sum, item) => sum + item.quantity, 0);

  return {
    totalOrders: items.length,
    totalMeals,
    lunchCount,
    dinnerCount,
    pendingDispatchCount: items.filter((item) => item.status === "PENDING_DISPATCH").length,
    priorityOrderCount: items.filter((item) => item.priorityCustomer).length,
    remarkedOrderCount: items.filter((item) => hasRemark(item)).length,
    confirmationCount: confirmationItems.length,
    priorityConfirmationCount: confirmationItems.filter((item) => Boolean(item.priority)).length
  };
}

export function buildOrderPrepCompactSummary(
  stats: Pick<
    import("../../shared/api/types").OrderPrepStatsResponse,
    "totalMeals" | "lunchCount" | "dinnerCount"
  >,
  summary: Pick<ReturnType<typeof buildOrderPrepSummary>, "confirmationCount" | "totalMeals" | "lunchCount" | "dinnerCount">
) : OrderPrepCompactSummaryItem[] {
  return [
    {
      label: "当前待出餐",
      value: `${summary.totalMeals} 份`,
      tone: "blue"
    },
    {
      label: "餐次结构",
      value: `${summary.lunchCount} / ${summary.dinnerCount}`,
      tone: "orange"
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
