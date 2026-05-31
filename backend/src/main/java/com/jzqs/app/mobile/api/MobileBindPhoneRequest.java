package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record MobileBindPhoneRequest(
    @NotBlank(message = "openid is required")
    String openid,
    @NotBlank(message = "phone is required")
    String phone,
    String nickname
) {
}
