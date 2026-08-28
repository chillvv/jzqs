export function normalizeDashboardOverview(data) {
    return {
        deliveredToday: data.deliveredToday ?? 0,
        tomorrowMealCount: data.tomorrowMealCount ?? 0,
        tomorrowLunchCount: data.tomorrowLunchCount ?? 0,
        tomorrowDinnerCount: data.tomorrowDinnerCount ?? 0,
        newCardsToday: data.newCardsToday ?? 0,
        rechargeCustomersToday: data.rechargeCustomersToday ?? 0,
        aftersaleToday: data.aftersaleToday ?? 0,
        cancellationsToday: data.cancellationsToday ?? 0,
        totalOrdersToday: data.totalOrdersToday ?? 0,
        pendingOrdersToday: data.pendingOrdersToday ?? 0,
        pendingDispatchToday: data.pendingDispatchToday ?? 0,
        dispatchingOrdersToday: data.dispatchingOrdersToday ?? 0,
        deliveredOrdersToday: data.deliveredOrdersToday ?? data.deliveredToday ?? 0,
        lowBalanceCustomers: data.lowBalanceCustomers ?? 0,
        openAftersaleCount: data.openAftersaleCount ?? 0,
        specialOrdersToday: data.specialOrdersToday ?? 0,
        menuRiskDays: data.menuRiskDays ?? 0,
        orderTrend: Array.isArray(data.orderTrend) ? data.orderTrend : [],
        growthTrend: Array.isArray(data.growthTrend) ? data.growthTrend : []
    };
}

export function buildDashboardHeroMetrics(data) {
    return [
        {
            label: "今日送达总量",
            value: data.deliveredToday,
            unit: "份",
            tone: "blue",
            detail: "按今日送达回执统计"
        },
        {
            label: "明日已下单份数",
            value: data.tomorrowMealCount,
            unit: "份",
            tone: "cyan",
            detail: `午餐 ${data.tomorrowLunchCount} / 晚餐 ${data.tomorrowDinnerCount}`
        },
        {
            label: "今日新开卡人数",
            value: data.newCardsToday,
            unit: "人",
            tone: "emerald",
            detail: "按今日开卡或首次建卡统计"
        },
        {
            label: "今日续卡 / 充值人数",
            value: data.rechargeCustomersToday,
            unit: "人",
            tone: "violet",
            detail: "按今日续卡和充餐动作统计"
        },
        {
            label: "今日售后单数",
            value: data.aftersaleToday,
            unit: "单",
            tone: "amber",
            detail: "只统计今天新增和处理中"
        },
        {
            label: "今日取消单数",
            value: data.cancellationsToday,
            unit: "单",
            tone: "red",
            detail: "用于观察当天经营损失"
        }
    ];
}

export function buildDashboardProgressItems(data) {
    return [
        { label: "今日总订单", value: data.totalOrdersToday, tone: "blue", detail: "今天全部进入流程的订单" },
        { label: "待处理", value: data.pendingOrdersToday, tone: "amber", detail: "待人工确认或继续流转" },
        { label: "待派单", value: data.pendingDispatchToday, tone: "violet", detail: "已确认但未指派骑手" },
        { label: "配送中", value: data.dispatchingOrdersToday, tone: "blue", detail: "骑手已接单，正在履约" },
        { label: "已送达", value: data.deliveredOrdersToday, tone: "emerald", detail: "当前已闭环送达" }
    ];
}

export function buildDashboardExceptionItems(data) {
    return [
        { label: "低余额客户", value: data.lowBalanceCustomers, tone: "amber", detail: "余额小于等于 3 餐" },
        { label: "待处理售后", value: data.openAftersaleCount, tone: "red", detail: "今日新增与未闭环售后" },
        { label: "特殊备注订单", value: data.specialOrdersToday, tone: "violet", detail: "重点客户、贴签、老板备注" },
        { label: "菜单配置风险", value: data.menuRiskDays, tone: "blue", detail: "未来 7 天仍有未配置餐槽" }
    ];
}

export function buildDashboardOrderTrendSummary(data) {
    const orderTrend = data.orderTrend ?? [];
    const totals = orderTrend.map((item) => item.total);
    const lunches = orderTrend.map((item) => item.lunch);
    const dinners = orderTrend.map((item) => item.dinner);
    const peak = orderTrend.reduce(
        (current, item) => (item.total > current.total ? item : current),
        orderTrend[0] ?? { label: "-", total: 0, lunch: 0, dinner: 0 }
    );
    const totalAverage = totals.length ? Math.round(totals.reduce((sum, value) => sum + value, 0) / totals.length) : 0;
    const lunchTotal = lunches.reduce((sum, value) => sum + value, 0);
    const dinnerTotal = dinners.reduce((sum, value) => sum + value, 0);
    const lunchShare = lunchTotal + dinnerTotal === 0 ? 0 : Math.round((lunchTotal / (lunchTotal + dinnerTotal)) * 100);
    return {
        peakValue: peak.total,
        peakLabel: peak.label,
        averageValue: totalAverage,
        lunchShare,
        rangeText: totals.length ? `${Math.min(...totals)}-${Math.max(...totals)}` : "0-0"
    };
}
