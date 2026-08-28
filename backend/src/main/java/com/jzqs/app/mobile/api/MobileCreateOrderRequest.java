package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record MobileCreateOrderRequest(
    @NotBlank(message = "serveDate is required")
    String serveDate,
    @NotBlank(message = "mealPeriod is required")
    String mealPeriod,
    String deliveryAddress,
    String note,
    Integer quantity,
    // 客户端每次下单生成的唯一请求 ID（时间戳+随机数），用于区分「有意加餐」与
    // 「同一操作的重试」。幂等 key 以 (customerId + 请求体 + clientRequestId) 为准：
    // 加餐生成新 ID 不命中幂等；同一次操作网络重试复用同一 ID 仍会被拦截。
    String clientRequestId
) {
    public int quantityOrDefault() {
        return quantity == null || quantity <= 0 ? 1 : quantity;
    }
}
