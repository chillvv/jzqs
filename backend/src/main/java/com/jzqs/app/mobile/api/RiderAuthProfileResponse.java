package com.jzqs.app.mobile.api;

public record RiderAuthProfileResponse(
    long riderId,
    String riderName,
    String displayName,
    String phone,
    String areaCode,
    String riderStatus,
    boolean workbenchEnabled,
    String firstLoginAt,
    String lastLoginAt
) {
}
