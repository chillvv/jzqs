package com.jzqs.app.customer.api;

public record CustomerAddressDetailResponse(
    long id,
    String contactName,
    String contactPhone,
    String addressLine,
    String areaCode,
    boolean isDefault
) {
}
