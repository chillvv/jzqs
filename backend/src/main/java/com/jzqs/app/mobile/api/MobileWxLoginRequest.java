package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record MobileWxLoginRequest(
    @NotBlank(message = "code is required")
    String code
) {
}
