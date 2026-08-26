package com.jzqs.app.mobile.api;

public record RiderAuthStateResponse(
    String authMode,
    String openid,
    boolean registered,
    boolean needPhoneAuth,
    Long riderId,
    String riderName,
    String displayName,
    String phone,
    String areaCode,
    String riderStatus,
    boolean workbenchEnabled,
    String firstLoginAt,
    String lastLoginAt,
    String token
) {
}
