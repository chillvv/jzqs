package com.jzqs.app.auth;

public record AuthVerifyResponse(
    boolean valid,
    Long userId,
    String userType
) {
}
