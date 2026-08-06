package com.jzqs.app.customer.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 批量统一延长有效期请求：节假日等场景下，给所有未过期客户统一增加到期时间。
 */
public record CustomerBatchExtendRequest(
    @Min(1) @Max(3650) int extendDays,
    @NotBlank String remark
) {
}
