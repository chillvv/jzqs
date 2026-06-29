package com.jzqs.app.order.api;

public record ManualCreateCustomerAddressResponse(
    long addressId,
    String addressLine,
    String areaCode,
    boolean isDefault
) {
}
