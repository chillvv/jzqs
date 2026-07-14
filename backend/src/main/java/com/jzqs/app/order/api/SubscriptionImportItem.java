package com.jzqs.app.order.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SubscriptionImportItem(
    @Min(value = 1, message = "客户ID不能为空") long customerId,
    @NotBlank(message = "餐次不能为空") String mealPeriod,
    String deliveryMealPeriod,
    @Min(value = 1, message = "配送地址不能为空") long addressId,
    String note
) {
}
