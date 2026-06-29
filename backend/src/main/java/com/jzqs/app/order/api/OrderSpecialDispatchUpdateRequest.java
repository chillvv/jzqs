package com.jzqs.app.order.api;

import jakarta.validation.constraints.NotBlank;

public record OrderSpecialDispatchUpdateRequest(
    @NotBlank(message = "请选择配送时间") String deliveryMealPeriod
) {
}
