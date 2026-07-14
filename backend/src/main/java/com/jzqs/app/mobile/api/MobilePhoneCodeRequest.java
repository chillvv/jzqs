package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record MobilePhoneCodeRequest(
    String openid,
    @NotBlank(message = "code is required")
    String code
) {
}
