package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record RiderTokenVerifyRequest(
    @NotBlank(message = "token is required") String token
) {
}
