package com.jzqs.app.mobile.api;

public record MobileAddressResponse(
    long id,
    String contactName,
    String contactPhone,
    String addressLine,
    String areaCode,
    boolean isDefault
) {
}
