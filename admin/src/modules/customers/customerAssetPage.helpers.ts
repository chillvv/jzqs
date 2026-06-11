import type {
  CustomerAssetResponse,
  CustomerDetailResponse,
  CustomerNoteItem
} from "../../shared/api/types";

export type CustomerBalanceState = "ALL" | "HAS_BALANCE" | "NO_BALANCE" | "LOW_BALANCE";
export type CustomerOrderModeFilter = "ALL" | "NORMAL" | "SUBSCRIPTION";

export type CustomerOverviewSummaryItem = {
  label: string;
  value: string;
  tone: "slate" | "amber";
};

export type CustomerAssetFilters = {
  keyword: string;
  customerStatus: string;
  balanceState: CustomerBalanceState;
  orderMode: CustomerOrderModeFilter;
};

export function buildCustomerAssetStats(items: CustomerAssetResponse[]) {
  return {
    intentionCount: items.filter((item) => item.customerStatus === "INTENTION").length,
    formalCount: items.filter((item) => item.customerStatus === "FORMAL").length,
    dormantCount: items.filter((item) => item.customerStatus === "DORMANT").length,
    fixedSubscriptionCount: items.filter((item) => item.fixedSubscriptionEnabled).length
  };
}

export function filterCustomerAssets(items: CustomerAssetResponse[], filters: CustomerAssetFilters) {
  const keyword = filters.keyword.trim();

  return items.filter((item) => {
    const matchesKeyword = keyword.length === 0
      || item.name.includes(keyword)
      || item.phone.includes(keyword)
      || (item.remark ?? "").includes(keyword);

    const matchesStatus = filters.customerStatus === "ALL" || item.customerStatus === filters.customerStatus;

    const matchesBalance = filters.balanceState === "ALL"
      || (filters.balanceState === "HAS_BALANCE" && item.remainingMeals > 0)
      || (filters.balanceState === "NO_BALANCE" && item.remainingMeals <= 0)
      || (filters.balanceState === "LOW_BALANCE" && item.hasOpenedCard && item.remainingMeals > 0 && item.remainingMeals <= 3);

    const matchesOrderMode = filters.orderMode === "ALL"
      || (filters.orderMode === "NORMAL" && !item.fixedSubscriptionEnabled)
      || (filters.orderMode === "SUBSCRIPTION" && item.fixedSubscriptionEnabled);

    return matchesKeyword && matchesStatus && matchesBalance && matchesOrderMode;
  });
}

export function buildCustomerPortfolioSummary(items: CustomerAssetResponse[]) {
  return {
    lowBalanceCount: items.filter((item) => item.hasOpenedCard && item.remainingMeals > 0 && item.remainingMeals <= 3).length,
    exhaustedCount: items.filter((item) => item.hasOpenedCard && (item.remainingMeals <= 0 || item.status === "EXHAUSTED")).length,
    vipCount: items.filter((item) => item.priorityCustomer).length,
    recentActiveCount: items.filter((item) => item.status === "ACTIVE" && Boolean(item.lastOrderAt)).length
  };
}

export function buildCustomerOverviewSummary(
  stats: Pick<ReturnType<typeof buildCustomerAssetStats>, "formalCount" | "fixedSubscriptionCount">
): CustomerOverviewSummaryItem[] {
  return [
    {
      label: "正式用户",
      value: `${stats.formalCount} 人`,
      tone: "slate"
    },
    {
      label: "固定订餐",
      value: `${stats.fixedSubscriptionCount} 人`,
      tone: "slate"
    }
  ];
}

export function buildCustomerActionLabels() {
  return ["详情资料", "扣餐"];
}

export function resolveCustomerSpecialMark(remark: string | null | undefined) {
  const text = (remark ?? "").trim();
  return text.length > 0 ? text : null;
}

export function resolveCustomerStatusLabel(status: string) {
  if (status === "INTENTION") return "意向客户";
  if (status === "FORMAL") return "正式客户";
  if (status === "DORMANT") return "沉睡客户";
  return status || "-";
}

export function resolveCustomerOrderModeLabel(item: CustomerAssetResponse) {
  return item.fixedSubscriptionEnabled ? "固定订餐" : "普通下单";
}

export function extractCustomerNoteGroups(detail: CustomerDetailResponse | null | undefined) {
  const userNotes = Array.isArray(detail?.userNotes) ? detail.userNotes as CustomerNoteItem[] : [];
  const longTermMerchantNotes = Array.isArray(detail?.longTermMerchantNotes)
    ? detail.longTermMerchantNotes as CustomerNoteItem[]
    : [];
  const timeBoxedMerchantNotes = Array.isArray(detail?.timeBoxedMerchantNotes)
    ? detail.timeBoxedMerchantNotes as CustomerNoteItem[]
    : [];

  return {
    userNotes,
    longTermMerchantNotes,
    timeBoxedMerchantNotes
  };
}

export function formatCustomerNoteSchedule(note: CustomerNoteItem) {
  if (note.scopeType !== "TIME_BOXED") {
    return "长期生效";
  }
  if (!note.startAt && !note.endAt) {
    return "限时生效";
  }
  return `${note.startAt || "-"} ~ ${note.endAt || "-"}`;
}
