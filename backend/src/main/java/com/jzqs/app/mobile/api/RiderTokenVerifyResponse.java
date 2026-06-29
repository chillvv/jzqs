package com.jzqs.app.mobile.api;

public record RiderTokenVerifyResponse(
    String openid,
    long riderId,
    String name,
    String displayName,
    String phone,
    String areaCode,
    String status,
    boolean workbenchEnabled,
    String firstLoginAt,
    String lastLoginAt
) {
}
