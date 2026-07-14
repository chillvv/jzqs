package com.jzqs.app.auth;

public record AuthLoginResponse(
    String token,
    Long userId,
    String userType,
    boolean registered,
    String openid
) {
}
