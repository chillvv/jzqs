export function buildAftersaleStats(items) {
    return {
        totalCount: items.length,
        openCount: items.filter((item) => item.status === "OPEN").length,
        resolvedCount: items.filter((item) => item.status === "RESOLVED").length,
        rollbackCount: items.filter((item) => item.rollbackMeal).length,
        compensationCount: items.filter((item) => item.bonusMeals > 0 || item.compensationItem.trim().length > 0).length
    };
}
export function buildAftersaleView(items, filters, currentPage, pageSize) {
    const keyword = filters.keyword.trim();
    const filteredItems = items.filter((item) => {
        const matchesKeyword = keyword.length === 0
            || item.customerName.includes(keyword)
            || item.customerPhone.includes(keyword)
            || item.issueType.includes(keyword)
            || item.issueDesc.includes(keyword)
            || item.compensationItem.includes(keyword)
            || item.operatorName.includes(keyword);
        const matchesStatus = filters.status === "ALL" || item.status === filters.status;
        const matchesResolution = filters.resolutionType === "ALL" || item.resolutionType === filters.resolutionType;
        return matchesKeyword && matchesStatus && matchesResolution;
    });
    const totalItems = filteredItems.length;
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    const safeCurrentPage = Math.min(Math.max(currentPage, 1), totalPages);
    const startIndex = (safeCurrentPage - 1) * pageSize;
    return {
        filteredItems,
        pageItems: filteredItems.slice(startIndex, startIndex + pageSize),
        totalItems,
        totalPages,
        currentPage: safeCurrentPage
    };
}
export function formatCompensationSummary(item) {
    const segments = [];
    if (item.rollbackMeal) {
        segments.push("撤回1餐");
    }
    if (item.bonusMeals > 0) {
        segments.push(`补${item.bonusMeals}餐`);
    }
    if (item.compensationItem.trim()) {
        segments.push(item.compensationItem.trim());
    }
    return segments.length > 0 ? segments.join(" + ") : "-";
}
export function resolveAftersaleStatusTone(status) {
    if (status === "OPEN") {
        return "orange";
    }
    if (status === "RESOLVED") {
        return "green";
    }
    return "gray";
}
export function resolveAftersaleStatusLabel(status) {
    if (status === "OPEN") {
        return "待处理";
    }
    if (status === "RESOLVED") {
        return "已处理";
    }
    return status || "-";
}
export function resolveIssueTypeLabel(issueType) {
    switch (issueType) {
        case "DELIVERY_EXCEPTION":
            return "配送异常";
        case "FOOD_SAFETY":
            return "餐品异常";
        case "SERVICE":
            return "服务异常";
        case "OTHER":
            return "其他";
        default:
            return issueType || "-";
    }
}
export function resolveResolutionLabel(resolutionType) {
    switch (resolutionType) {
        case "REGISTER_ONLY":
            return "仅登记";
        case "ROLLBACK_ONLY":
            return "撤回扣餐";
        case "ROLLBACK_AND_COMPENSATE":
            return "撤回并补偿";
        default:
            return resolutionType || "-";
    }
}
