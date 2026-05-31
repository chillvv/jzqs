package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record MobileCompleteProfileRequest(
    @NotBlank(message = "openid is required")
    String openid,
    @NotBlank(message = "nickname is required")
    String nickname
) {
}
