import type { AdminAftersaleItemResponse } from "../../shared/api/types";

export type AftersaleStatusKey = "PENDING" | "PROCESSING" | "COMPLETED" | "REJECTED";
export type AftersaleTone = "orange" | "blue" | "green" | "red" | "gray";

export type AftersaleTabItem = {
  key: AftersaleStatusKey;
  label: string;
  count: number;
};

export type AftersaleResolveAction = "REFUND_TO_WALLET" | "COMPENSATE_MEALS" | "REGISTER_ONLY" | "REJECT";

export type AftersaleResolveFormState = {
  action: AftersaleResolveAction;
  walletDelta: number;
  settledLossMeals: number;
  giftZeroMealCount: number;
  giftVeggieJuiceCount: number;
  adminRemark: string;
  refundBlocking: boolean;
};

export type AftersaleViewFilters = {
  status: AftersaleStatusKey | "ALL";
  type: string;
  keyword: string;
  hideAutoRefund: boolean;
};

const AFTERSALE_STATUS_LABELS: Record<AftersaleStatusKey, string> = {
  PENDING: "待处理",
  PROCESSING: "处理中",
  COMPLETED: "已完成",
  REJECTED: "已驳回"
};

const AFTERSALE_COMPACT_STATUS_LABELS: Record<AftersaleStatusKey, string> = {
  PENDING: "待处理",
  PROCESSING: "处理中",
  COMPLETED: "已完成",
  REJECTED: "未通过"
};

export function buildAftersaleTabs(
  pendingCount: number,
  processingCount: number,
  completedCount: number,
  rejectedCount: number
): AftersaleTabItem[] {
  return [
    { key: "PENDING", label: AFTERSALE_STATUS_LABELS.PENDING, count: pendingCount },
    { key: "PROCESSING", label: AFTERSALE_STATUS_LABELS.PROCESSING, count: processingCount },
    { key: "COMPLETED", label: AFTERSALE_STATUS_LABELS.COMPLETED, count: completedCount },
    { key: "REJECTED", label: AFTERSALE_STATUS_LABELS.REJECTED, count: rejectedCount }
  ];
}

export function resolveAftersaleTone(status: string, type?: string): AftersaleTone {
  if (status === "COMPLETED") {
    return type === "REFUND" ? "green" : "blue";
  }
  if (status === "PENDING") {
    return "orange";
  }
  if (status === "PROCESSING") {
    return "blue";
  }
  if (status === "REJECTED") {
    return "red";
  }
  return "gray";
}

export function countAftersalesByStatus(items: AdminAftersaleItemResponse[], status: AftersaleStatusKey) {
  return items.filter((item) => item.status === status).length;
}

export function buildAftersaleView(items: AdminAftersaleItemResponse[], filters: AftersaleViewFilters) {
  const keyword = filters.keyword.trim();
  return items.filter((item) => {
    const matchesStatus = filters.status === "ALL" || item.status === filters.status;
    const matchesType = filters.type === "ALL" || item.type === filters.type;
    const matchesAutoRefund = !filters.hideAutoRefund || item.sourceCategory !== "AUTO_REFUND";
    const matchesKeyword =
      keyword.length === 0
      || item.customerName.includes(keyword)
      || item.customerPhone.includes(keyword)
      || item.reasonText.includes(keyword)
      || item.reasonCode.includes(keyword)
      || item.issueParamSummary.includes(keyword)
      || String(item.orderId).includes(keyword);

    return matchesStatus && matchesType && matchesAutoRefund && matchesKeyword;
  });
}

export function resolveAftersaleTypeLabel(type: string) {
  if (type === "REFUND") {
    return "退款";
  }
  if (type === "COMPENSATION") {
    return "补偿";
  }
  return type || "售后";
}

export function resolveAftersaleSourceLabel(source: string) {
  if (source === "AUTO_REFUND") {
    return "秒退款";
  }
  if (source === "USER_APPLY") {
    return "用户申请";
  }
  if (source === "ADMIN_DIRECT") {
    return "后台发起";
  }
  return source || "-";
}

export function resolveAftersaleStatusLabel(status: string) {
  return AFTERSALE_STATUS_LABELS[status as AftersaleStatusKey] ?? status ?? "-";
}

export function resolveAftersaleCompactStatusLabel(status: string) {
  return AFTERSALE_COMPACT_STATUS_LABELS[status as AftersaleStatusKey] ?? status ?? "-";
}

export function resolveMealPeriodLabel(mealPeriod: string) {
  if (mealPeriod === "LUNCH") {
    return "午餐";
  }
  if (mealPeriod === "DINNER") {
    return "晚餐";
  }
  return mealPeriod || "-";
}

export function resolveAftersaleAvailableActions(type: string, status: string): AftersaleResolveAction[] {
  if (status === "COMPLETED" || status === "REJECTED") {
    return [];
  }
  if (type === "REFUND") {
    return ["REFUND_TO_WALLET", "REJECT"];
  }
  return ["COMPENSATE_MEALS", "REGISTER_ONLY", "REJECT"];
}

export function buildAftersaleResolveFormState(type: string): AftersaleResolveFormState {
  return {
    action: type === "REFUND" ? "REFUND_TO_WALLET" : "COMPENSATE_MEALS",
    walletDelta: 1,
    settledLossMeals: 0,
    giftZeroMealCount: 0,
    giftVeggieJuiceCount: 0,
    adminRemark: "",
    refundBlocking: false
  };
}

export function resolveSettlementSummary(item: Pick<
  AdminAftersaleItemResponse,
  "resolutionAction" | "walletDelta" | "giftZeroMealCount" | "giftVeggieJuiceCount"
>) {
  const parts: string[] = [];
  if (item.resolutionAction === "REGISTER_ONLY") {
    parts.push("仅登记");
  } else if (item.resolutionAction === "REFUND_TO_WALLET") {
    parts.push(`退餐次 ${item.walletDelta}`);
  } else if (item.resolutionAction === "COMPENSATE_MEALS") {
    parts.push(`补餐次 ${item.walletDelta}`);
  }
  if (item.giftZeroMealCount > 0) {
    parts.push(`补零餐 ${item.giftZeroMealCount}`);
  }
  if (item.giftVeggieJuiceCount > 0) {
    parts.push(`果蔬汁 ${item.giftVeggieJuiceCount}`);
  }
  return parts.length > 0 ? parts.join(" / ") : "-";
}
