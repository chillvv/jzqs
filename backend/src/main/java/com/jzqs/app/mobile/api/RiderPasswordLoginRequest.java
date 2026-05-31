package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record RiderPasswordLoginRequest(
    @NotBlank(message = "手机号不能为空") String phone,
    String password
) {
}
