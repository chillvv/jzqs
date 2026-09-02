package com.jzqs.app.mobile.api;

import java.math.BigDecimal;

public record MobileAddressResponse(
    long id,
    String contactName,
    String contactPhone,
    String addressLine,
    String doorNumber,
    String areaCode,
    boolean isDefault,
    BigDecimal latitude,
    BigDecimal longitude
) {
}
