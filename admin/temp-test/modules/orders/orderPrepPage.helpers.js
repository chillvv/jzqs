export function formatOrderNote(value) {
    const trimmed = value?.trim() ?? "";
    return trimmed || "-";
}
export function resolveMealPeriod(item) {
    return item.mealSummary.includes("晚餐") ? "DINNER" : "LUNCH";
}
export function resolveOrderSourceLabel(item) {
    if (item.fixedSubscription) {
        return "固定订餐";
    }
    if (item.source === "BACKEND") {
        return "后台录入";
    }
    return "小程序";
}
export function resolveOrderDisplayStatus(item) {
    return item.displayStatus || item.status;
}
export function resolveOrderDisplayStatusLabel(status) {
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
export function resolveOrderStatusTone(status) {
    if (status === "REFUND_PROCESSING") {
        return "red";
    }
    if (status === "DISPATCHING") {
        return "blue";
    }
    if (status === "DELIVERED") {
        return "green";
    }
    if (status === "REFUNDED" || status === "CANCELLED") {
        return "gray";
    }
    return "orange";
}
export function buildMealPrepExportRows(items) {
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
            "后台备注": formatOrderNote(item.adminNote),
            "特殊标签": formatOrderNote(item.specialTag),
            "订单状态": item.displayStatusLabel || resolveOrderDisplayStatusLabel(displayStatus)
        };
    });
}
export function buildOrderPrepView(items, filters, currentPage, pageSize) {
    const keyword = filters.keyword.trim();
    const filteredItems = items.filter((item) => {
        const matchesKeyword = keyword.length === 0
            || item.customerName.includes(keyword)
            || item.customerPhone.includes(keyword)
            || item.mealSummary.includes(keyword)
            || formatOrderNote(item.userNote).includes(keyword)
            || formatOrderNote(item.adminNote).includes(keyword)
            || formatOrderNote(item.specialTag).includes(keyword);
        const matchesMealPeriod = filters.mealPeriod === "ALL" || resolveMealPeriod(item) === filters.mealPeriod;
        const sourceLabel = resolveOrderSourceLabel(item);
        const matchesSource = filters.source === "ALL"
            || (filters.source === "MINIAPP" && sourceLabel === "小程序")
            || (filters.source === "BACKEND" && sourceLabel === "后台录入")
            || (filters.source === "SUBSCRIPTION" && sourceLabel === "固定订餐");
        const matchesStatus = filters.status === "ALL" || resolveOrderDisplayStatus(item) === filters.status;
        return matchesKeyword && matchesMealPeriod && matchesSource && matchesStatus;
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
export function buildOrderPrepSummary(items, confirmationItems, specialOrders) {
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
        priorityOrderCount: items.filter((item) => item.priorityCustomer || formatOrderNote(item.specialTag) !== "-").length,
        confirmationCount: confirmationItems.length,
        priorityConfirmationCount: confirmationItems.filter((item) => Boolean(item.priority)).length,
        specialOrderCount: specialOrders.length,
        prioritySpecialCount: specialOrders.filter((item) => Boolean(item.priorityCustomer)).length
    };
}

export function buildOrderPrepCompactSummary(stats, summary) {
    return [
        {
            label: "当前待出餐",
            value: `${stats.totalMeals} 份`,
            tone: "blue"
        },
        {
            label: "餐次结构",
            value: `${stats.lunchCount} / ${stats.dinnerCount}`,
            tone: "orange"
        },
        {
            label: "待确认固定订餐",
            value: `${summary.confirmationCount} 份`,
            tone: "red"
        }
    ];
}

export function buildOrderPrepDefaultTab(confirmationCount) {
    return confirmationCount > 0 ? "CONFIRMATION" : "ORDERS";
}

export function buildSubscriptionConfirmationPanelState(confirmationCount) {
    return {
        visible: confirmationCount > 0,
        expanded: confirmationCount > 0
    };
}
