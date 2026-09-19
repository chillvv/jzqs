package com.jzqs.app.dispatch.api;

public record DispatchOverviewResponse(
    int pendingCount,
    int dispatchingCount,
    int missingRiderAreaCount,
    /** 跨餐配送·转出：本餐次出餐、却改到另一餐次配送的份数（如午餐出餐晚餐送），不进入本餐次骑手队列 */
    int crossMealDeliveryOutCount,
    /** 跨餐配送·转入：别的餐次出餐、改到本餐次配送的份数（本餐次会多出这些单） */
    int crossMealDeliveryInCount
) {
}
