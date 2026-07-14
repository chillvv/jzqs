package com.jzqs.app.order.api;

import jakarta.validation.constraints.NotBlank;

public record OrderMerchantRemarkUpdateRequest(
    @NotBlank(message = "商家备注不能为空") String merchantRemark
) {
}
