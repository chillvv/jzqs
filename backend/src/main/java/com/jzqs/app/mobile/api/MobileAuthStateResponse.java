package com.jzqs.app.mobile.api;

public record MobileAuthStateResponse(
    String authMode,
    String openid,
    String sessionKey,
    boolean registered,
    boolean needPhoneAuth,
    boolean needName,
    String token,
    Long customerId
) {
}
