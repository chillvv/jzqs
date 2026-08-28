export function buildCustomerAssetStats(items) {
    return {
        intentionCount: items.filter((item) => item.customerStatus === "INTENTION").length,
        formalCount: items.filter((item) => item.customerStatus === "FORMAL").length,
        dormantCount: items.filter((item) => item.customerStatus === "DORMANT").length,
        priorityCount: items.filter((item) => item.priorityCustomer).length,
        fixedSubscriptionCount: items.filter((item) => item.fixedSubscriptionEnabled).length,
        balanceCount: items.filter((item) => item.remainingMeals > 0).length,
        noBalanceCount: items.filter((item) => item.remainingMeals <= 0).length
    };
}
export function filterCustomerAssets(items, filters) {
    const keyword = filters.keyword.trim();
    return items.filter((item) => {
        const matchesKeyword = keyword.length === 0
            || item.name.includes(keyword)
            || item.phone.includes(keyword)
            || (item.priorityTag ?? "").includes(keyword)
            || (item.remark ?? "").includes(keyword);
        const matchesStatus = filters.customerStatus === "ALL" || item.customerStatus === filters.customerStatus;
        const matchesBalance = filters.balanceState === "ALL"
            || (filters.balanceState === "HAS_BALANCE" && item.remainingMeals > 0)
            || (filters.balanceState === "NO_BALANCE" && item.remainingMeals <= 0)
            || (filters.balanceState === "LOW_BALANCE" && item.hasOpenedCard && item.remainingMeals > 0 && item.remainingMeals <= 3);
        const matchesPriority = !filters.priorityOnly || item.priorityCustomer;
        const matchesOrderMode = filters.orderMode === "ALL"
            || (filters.orderMode === "NORMAL" && !item.fixedSubscriptionEnabled)
            || (filters.orderMode === "SUBSCRIPTION" && item.fixedSubscriptionEnabled);
        return matchesKeyword && matchesStatus && matchesBalance && matchesPriority && matchesOrderMode;
    });
}
export function buildCustomerPortfolioSummary(items) {
    return {
        lowBalanceCount: items.filter((item) => item.hasOpenedCard && item.remainingMeals > 0 && item.remainingMeals <= 3).length,
        exhaustedCount: items.filter((item) => item.hasOpenedCard && (item.remainingMeals <= 0 || item.status === "EXHAUSTED")).length,
        vipCount: items.filter((item) => item.priorityCustomer).length,
        recentActiveCount: items.filter((item) => item.status === "ACTIVE" && Boolean(item.lastOrderAt)).length
    };
}

export function buildCustomerOverviewSummary(stats, _summary) {
    return [
        {
            label: "正式客户",
            value: `${stats.formalCount} 人`,
            tone: "blue"
        },
        {
            label: "无余额客户",
            value: `${stats.noBalanceCount} 人`,
            tone: "red"
        },
        {
            label: "固定订餐",
            value: `${stats.fixedSubscriptionCount} 人`,
            tone: "orange"
        }
    ];
}

export function resolveCustomerStatusLabel(status) {
    if (status === "INTENTION")
        return "意向客户";
    if (status === "FORMAL")
        return "正式客户";
    if (status === "DORMANT")
        return "沉睡客户";
    return status || "-";
}

export function resolveCustomerOrderModeLabel(item) {
    return item.fixedSubscriptionEnabled ? "固定订餐" : "普通下单";
}

export function resolveCustomerSpecialLabel(item) {
    if (!item.priorityCustomer) {
        return "-";
    }
    return item.priorityTag?.trim() || "重点客户";
}
