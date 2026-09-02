package com.jzqs.app.customer.api;

import java.math.BigDecimal;

public record CustomerAddressDetailResponse(
    long id,
    String contactName,
    String contactPhone,
    String addressLine,
    String areaCode,
    boolean isDefault,
    BigDecimal latitude,
    BigDecimal longitude
) {
}
