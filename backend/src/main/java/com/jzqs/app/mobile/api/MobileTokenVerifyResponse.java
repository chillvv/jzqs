package com.jzqs.app.mobile.api;

public record MobileTokenVerifyResponse(
    boolean valid,
    long userId,
    String userType
) {
}
