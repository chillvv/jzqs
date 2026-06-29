package com.jzqs.app.order.api;

import jakarta.validation.constraints.Min;

public record OrderProfileUpdateRequest(
    String mealPeriod,
    @Min(value = 1, message = "数量至少为1") Integer quantity,
    String deliveryAddress,
    String merchantRemark,
    Boolean priorityCustomer,
    String status
) {
}
