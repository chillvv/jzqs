package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MobilePhoneLoginRequest(
    String openid,
    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的11位手机号")
    String phone
) {
}
