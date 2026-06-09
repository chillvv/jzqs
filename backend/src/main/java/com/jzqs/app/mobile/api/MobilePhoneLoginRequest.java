package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record MobilePhoneLoginRequest(
    String openid,
    @NotBlank(message = "phone is required")
    String phone
) {
}
