package com.jzqs.app.customer.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record WalletAdjustRequest(
    @Min(1) int mealDelta,
    @Min(1) Integer validityDays,
    String operatorName,
    @NotBlank String remark,
    // 可选：直接指定加餐后的到期时间（yyyy-MM-dd 或 yyyy-MM-dd'T'HH:mm:ss）
    // 传入后优先使用，未传入时按 validityDays 从当前北京时间+天数计算
    String expiredAt
) {
    public WalletAdjustRequest(int mealDelta, Integer validityDays, String operatorName, String remark) {
        this(mealDelta, validityDays, operatorName, remark, null);
    }
}
