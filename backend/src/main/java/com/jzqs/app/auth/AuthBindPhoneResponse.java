package com.jzqs.app.auth;

public record AuthBindPhoneResponse(
    String token,
    Long userId,
    String userType,
    String phone,
    String riderName,
    String riderStatus,
    Boolean workbenchEnabled
) {
}
