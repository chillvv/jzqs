package com.jzqs.app.order.api;

import jakarta.validation.constraints.Min;

public record OrderProfileUpdateRequest(
    String mealPeriod,
    @Min(value = 1, message = "数量至少为1") Integer quantity,
    String deliveryAddress,
    // 显式地址 ID：后台编辑订单精确关联所选地址（哪个 ID 就是哪个地址），
    // 不再用 deliveryAddress 文本匹配。
    Long addressId,
    String merchantRemark,
    Boolean priorityCustomer,
    String status
) {
}
